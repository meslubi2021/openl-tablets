package org.openl.rules.repository.git;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.openl.rules.repository.RRepositoryFactory;
import org.openl.rules.repository.api.*;
import org.openl.rules.repository.common.ChangesMonitor;
import org.openl.rules.repository.common.RevisionGetter;
import org.openl.rules.repository.exceptions.RRepositoryException;
import org.openl.util.FileUtils;
import org.openl.util.IOUtils;
import org.openl.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitRepository implements FolderRepository, Closeable, RRepositoryFactory {
    private final Logger log = LoggerFactory.getLogger(GitRepository.class);

    private String uri;
    private String login;
    private String password;
    private String localRepositoryPath;
    private String branch = Constants.MASTER;
    private String tagPrefix = "";
    private int listenerTimerPeriod = 10;

    private ChangesMonitor monitor;
    private Git git;

    private ReadWriteLock repositoryLock = new ReentrantReadWriteLock();

    @Override
    public List<FileData> list(String path) throws IOException {
        return iterate(path, new ListCommand());
    }

    @Override
    public FileData check(String name) throws IOException {
        return iterate(name, new CheckCommand());
    }

    @Override
    public FileItem read(String name) throws IOException {
        return iterate(name, new ReadCommand());
    }

    @Override
    public FileData save(FileData data, InputStream stream) throws IOException {
        Lock writeLock = repositoryLock.writeLock();
        try {
            writeLock.lock();
            String fileInRepository = data.getName();
            File file = new File(localRepositoryPath, fileInRepository);
            createParent(file);
            IOUtils.copyAndClose(stream, new FileOutputStream(file));

            git.add().addFilepattern(fileInRepository).call();
            // TODO: Add possibility to set committer email
            RevCommit commit = git.commit().setMessage(data.getComment())
                    .setCommitter(data.getAuthor(), "")
                    .setOnly(fileInRepository)
                    .call();

            addTagToCommit(commit);

            push();
        } catch (Exception e) {
            reset();
            throw new IOException(e);
        } finally {
            writeLock.unlock();
        }

        return check(data.getName());
    }

    @Override
    public boolean delete(FileData data) {
        Lock writeLock = repositoryLock.writeLock();
        try {
            writeLock.lock();

            String fileInRepository = data.getName();
            File file = new File(localRepositoryPath, fileInRepository);
            if (!file.exists()) {
                return false;
            }

            git.rm().addFilepattern(fileInRepository).call();
            RevCommit commit = git.commit().setMessage(data.getComment()).setCommitter(data.getAuthor(), "").call();
            addTagToCommit(commit);

            push();

            return true;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            reset();
            return false;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public FileData copy(String srcName, FileData destData) throws IOException {
        Lock writeLock = repositoryLock.writeLock();
        try {
            writeLock.lock();
            File src = new File(localRepositoryPath, srcName);
            File dest = new File(localRepositoryPath, destData.getName());
            IOUtils.copyAndClose(new FileInputStream(src), new FileOutputStream(dest));

            git.add().addFilepattern(destData.getName()).call();
            RevCommit commit = git.commit()
                    .setMessage(destData.getComment())
                    .setCommitter(destData.getAuthor(), "")
                    .call();
            addTagToCommit(commit);

            push();
        } catch (Exception e) {
            reset();
            throw new IOException(e);
        } finally {
            writeLock.unlock();
        }

        return check(destData.getName());
    }

    @Override
    public FileData rename(String srcName, FileData destData) throws IOException {
        Lock writeLock = repositoryLock.writeLock();
        try {
            writeLock.lock();
            File src = new File(localRepositoryPath, srcName);
            File dest = new File(localRepositoryPath, destData.getName());
            FileUtils.move(src, dest);

            git.rm().addFilepattern(srcName).call();
            git.add().addFilepattern(destData.getName()).call();
            RevCommit commit = git.commit().setMessage(destData.getComment())
                    .setCommitter(destData.getAuthor(), "")
                    .setOnly(srcName)
                    .setOnly(destData.getName())
                    .call();
            addTagToCommit(commit);

            push();
        } catch (Exception e) {
            reset();
            throw new IOException(e);
        } finally {
            writeLock.unlock();
        }

        return check(destData.getName());
    }

    @Override
    public void setListener(Listener callback) {
        monitor.setListener(callback);
    }

    @Override
    public List<FileData> listHistory(String name) throws IOException {
        return iterateHistory(name, new ListHistoryVisitor());
    }

    @Override
    public FileData checkHistory(String name, String version) throws IOException {
        return iterateHistory(name, new CheckHistoryVisitor(version));
    }

    @Override
    public FileItem readHistory(String name, String version) throws IOException {
        return iterateHistory(name, new ReadHistoryVisitor(version));
    }

    @Override
    public boolean deleteHistory(String name, String version) {
        // Undelete and erase operations for this repository aren't supported. Just do nothing.
        return false;
    }

    @Override
    public FileData copyHistory(String srcName, FileData destData, String version) throws IOException {
        if (version == null) {
            return copy(srcName, destData);
        }

        FileItem fileItem = null;
        try {
            fileItem = readHistory(srcName, version);

            FileData copy = new FileData();
            copy.setName(destData.getName());
            copy.setComment(destData.getComment());
            copy.setAuthor(destData.getAuthor());
            copy.setSize(fileItem.getData().getSize());

            return save(copy, fileItem.getStream());
        } finally {
            if (fileItem != null) {
                IOUtils.closeQuietly(fileItem.getStream());
            }
        }
    }

    @Override
    public void initialize() throws RRepositoryException {
        try {
            File local = new File(localRepositoryPath);
            if (!local.exists()) {
                CloneCommand cloneCommand = Git.cloneRepository()
                        .setURI(uri)
                        .setDirectory(local)
                        .setBranch(branch)
                        .setBranchesToClone(Collections.singletonList(Constants.R_HEADS + branch));

                if (StringUtils.isNotBlank(login)) {
                    cloneCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(login, password));
                }

                Git cloned = cloneCommand.call();
                cloned.close();
            }

            git = Git.open(local);
            monitor = new ChangesMonitor(new GitRevisionGetter(), listenerTimerPeriod);
        } catch (Exception e) {
            throw new RRepositoryException(e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        if (git != null) {
            git.close();
        }
        if (monitor != null) {
            monitor.release();
            monitor = null;
        }
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setLocalRepositoryPath(String localRepositoryPath) {
        this.localRepositoryPath = localRepositoryPath;
    }

    public void setBranch(String branch) {
        this.branch = StringUtils.isBlank(branch) ? Constants.MASTER : branch;
    }

    public void setTagPrefix(String tagPrefix) {
        this.tagPrefix = tagPrefix;
    }

    public void setListenerTimerPeriod(int listenerTimerPeriod) {
        this.listenerTimerPeriod = listenerTimerPeriod;
    }

    private static TreeWalk buildTreeWalk(org.eclipse.jgit.lib.Repository repository, String path, RevTree tree) throws
                                                                                                          IOException {
        TreeWalk treeWalk;
        if (StringUtils.isEmpty(path)) {
            treeWalk = new TreeWalk(repository);
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            treeWalk.setPostOrderTraversal(false);
        } else {
            treeWalk = TreeWalk.forPath(repository, path, tree);
        }

        if (treeWalk == null) {
            throw new FileNotFoundException("Did not find expected path '" + path + "' in tree '" + tree.getName() + "'");
        }
        return treeWalk;
    }

    private FileData createFileData(org.eclipse.jgit.lib.Repository repository,
            TreeWalk dirWalk,
            String baseFolder) throws GitAPIException, IOException {
        String fullPath = baseFolder + dirWalk.getPathString();

        Iterator<RevCommit> iterator = git.log()
                .add(git.getRepository().resolve(branch))
                .addPath(fullPath)
                .call()
                .iterator();
        if (!iterator.hasNext()) {
            throw new IllegalStateException("Can't find revision for a file " + dirWalk.getPathString());
        }

        return createFileData(repository, dirWalk, baseFolder, iterator.next());
    }

    private FileData createFileData(org.eclipse.jgit.lib.Repository repository,
            TreeWalk dirWalk,
            String baseFolder,
            RevCommit fileCommit) throws GitAPIException, IOException {
        String fullPath = baseFolder + dirWalk.getPathString();

        FileData fileData = new FileData();
        fileData.setName(fullPath);

        PersonIdent committerIdent = fileCommit.getCommitterIdent();

        fileData.setAuthor(committerIdent.getName());
        fileData.setModifiedAt(committerIdent.getWhen());
        fileData.setComment(fileCommit.getFullMessage());
        fileData.setVersion(getVersionName(fileCommit.getId()));

        ObjectLoader loader = repository.open(dirWalk.getObjectId(0));
        fileData.setSize(loader.getSize());
        return fileData;
    }

    private FileData createFolderData(TreeWalk dirWalk, String baseFolder) throws GitAPIException, IOException {
        String fullPath = baseFolder + dirWalk.getPathString();

        Iterator<RevCommit> iterator = git.log()
                .add(git.getRepository().resolve(branch))
                .addPath(fullPath)
                .call()
                .iterator();
        if (!iterator.hasNext()) {
            throw new IllegalStateException("Can't find revision for a file " + dirWalk.getPathString());
        }

        return createFolderData(dirWalk, baseFolder, iterator.next());
    }

    private FileData createFolderData(TreeWalk dirWalk, String baseFolder, RevCommit commit) throws GitAPIException {
        String fullPath = baseFolder + dirWalk.getPathString();

        FileData fileData = new FileData();
        fileData.setName(fullPath + "/");

        PersonIdent committerIdent = commit.getCommitterIdent();

        fileData.setAuthor(committerIdent.getName());
        fileData.setModifiedAt(committerIdent.getWhen());
        fileData.setComment(commit.getFullMessage());
        fileData.setVersion(getVersionName(commit.getId()));

        return fileData;
    }

    private ObjectId getLastRevision() throws GitAPIException, IOException {
        pull();

        Lock readLock = repositoryLock.readLock();
        try {
            readLock.lock();
            return git.getRepository().resolve("HEAD^{tree}");
        } finally {
            readLock.unlock();
        }
    }

    private void pull() throws GitAPIException {
        Lock writeLock = repositoryLock.writeLock();
        try {
            writeLock.lock();

            // TODO: Consider changing merge strategy
            PullResult pullResult = git.pull().setStrategy(MergeStrategy.OURS).call();
            if (!pullResult.isSuccessful()) {
                throw new IllegalStateException("Can't pull: " + pullResult.toString());
            }
        } finally {
            writeLock.unlock();
        }
    }

    private void push() throws GitAPIException {
        Lock writeLock = repositoryLock.writeLock();
        try {
            writeLock.lock();

            PushCommand push = git.push().setPushTags().add(branch);

            if (StringUtils.isNotBlank(login)) {
                push.setCredentialsProvider(new UsernamePasswordCredentialsProvider(login, password));
            }

            push.call();

            monitor.fireOnChange();
        } finally {
            writeLock.unlock();
        }
    }

    private <T> T iterate(String path, WalkCommand<T> command) throws IOException {
        Lock readLock = repositoryLock.readLock();
        try {
            readLock.lock();

            org.eclipse.jgit.lib.Repository repository = git.getRepository();
            try (RevWalk walk = new RevWalk(repository)) {
                Ref head = repository.findRef(Constants.HEAD);
                RevCommit commit = walk.parseCommit(head.getObjectId());
                RevTree tree = commit.getTree();

                // Create TreeWalk for root folder
                try (TreeWalk rootWalk = buildTreeWalk(repository, path, tree)) {
                    return command.apply(repository, rootWalk, path);
                } catch (FileNotFoundException e) {
                    return command.apply(repository, null, path);
                }
            }
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
            readLock.unlock();
        }
    }

    private <T> T iterateHistory(String name, HistoryVisitor<T> historyVisitor) throws IOException {
        Lock readLock = repositoryLock.readLock();
        try {
            readLock.lock();
            org.eclipse.jgit.lib.Repository repository = git.getRepository();
            Iterator<RevCommit> iterator = git.log()
                    .add(repository.resolve(branch))
                    .addPath(name)
                    .call()
                    .iterator();

            List<Ref> call = git.tagList().call();

            while (iterator.hasNext()) {
                RevCommit commit = iterator.next();

                Ref tagRefForCommit = getTagRefForCommit(call, commit.getId());
                if (tagRefForCommit == null) {
                    // Skip commits without tags
                    continue;
                }

                boolean stop = historyVisitor.visit(name, commit, tagRefForCommit);
                if (stop) {
                    break;
                }
            }

            return historyVisitor.getResult();
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
            readLock.unlock();
        }
    }

    private void reset() {
        try {
            git.reset().setMode(ResetCommand.ResetType.HARD).call();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private String getNextTagId() throws GitAPIException {
        List<Ref> call = git.tagList().call();
        long maxId = 0;
        for (Ref tagRef : call) {
            String name = getLocalTagName(tagRef);
            if (name.startsWith(tagPrefix)) {
                int num;
                try {
                    num = Integer.parseInt(name.substring(tagPrefix.length()));
                } catch (NumberFormatException e) {
                    log.debug("Tag " + name + " is skipped because it doesn't contain version number");
                    continue;
                }
                if (num > maxId) {
                    maxId = num;
                }
            }
        }

        return String.valueOf(maxId + 1);
    }

    private String getVersionName(ObjectId commitId) throws GitAPIException {
        List<Ref> call = git.tagList().call();
        Ref tagRef = getTagRefForCommit(call, commitId);

        return tagRef != null ? getLocalTagName(tagRef) : commitId.getName();
    }

    private Ref getTagRefForCommit(List<Ref> call, ObjectId commitId) {
        Ref tagRefForCommit = null;
        for (Ref tagRef : call) {
            ObjectId objectId = git.getRepository().peel(tagRef).getPeeledObjectId();
            if (objectId == null) {
                objectId = tagRef.getObjectId();
            }

            if (objectId.equals(commitId)) {
                tagRefForCommit = tagRef;
                break;
            }
        }
        return tagRefForCommit;
    }

    private String getLocalTagName(Ref tagRef) {
        String name = tagRef.getName();
        return name.startsWith(Constants.R_TAGS) ? name.substring(Constants.R_TAGS.length()) : name;
    }

    private void addTagToCommit(RevCommit commit) throws GitAPIException {
        git.tag().setObjectId(commit).setName(tagPrefix + getNextTagId()).call();
    }

    @Override
    public List<FileData> listFolders(String path) throws IOException {
        return iterate(path, new ListFoldersCommand());
    }

    @Override
    public FileData save(FileData folderData, List<FileChange> files) throws IOException {
        Lock writeLock = repositoryLock.writeLock();
        try {
            writeLock.lock();
            String relativeFolder = folderData.getName();

            // Remove absent files
            List<File> removed = new ArrayList<>();
            File folder = new File(localRepositoryPath, relativeFolder);
            listRemovedFiles(removed, folder, toFiles(files));
            for (File file : removed) {
                String basePath = new File(localRepositoryPath).getAbsolutePath();
                String fileInRepository = file.getAbsolutePath().substring(basePath.length()).replace('\\', '/');
                if (fileInRepository.startsWith("/")) {
                    fileInRepository = fileInRepository.substring(1);
                }
                git.rm().addFilepattern(fileInRepository).call();
            }

            // Add new files and update existing ones
            for (FileChange change : files) {
                File file = new File(localRepositoryPath, change.getName());
                createParent(file);
                
                IOUtils.copyAndClose(change.getStream(), new FileOutputStream(file));
                git.add().addFilepattern(change.getName()).call();
            }

            // TODO: Add possibility to set committer email
            RevCommit commit = git.commit().setMessage(folderData.getComment())
                    .setCommitter(folderData.getAuthor(), "")
                    .setOnly(relativeFolder)
                    .call();

            addTagToCommit(commit);

            push();
        } catch (Exception e) {
            reset();
            throw new IOException(e);
        } finally {
            writeLock.unlock();
        }

        return check(folderData.getName());
    }

    private List<File> toFiles(List<FileChange> paths) {
        List<File> files = new ArrayList<>();
        for (FileChange change : paths) {
            files.add(new File(localRepositoryPath, change.getName()));
        }
        return files;
    }

    private void listRemovedFiles(Collection<File> removed, File directory, Collection<File> toSave) {
        File[] found = directory.listFiles();

        if (found != null) {
            for (File file : found) {
                if (file.isDirectory()) {
                    listRemovedFiles(removed, file, toSave);
                } else {
                    if (!toSave.contains(file)) {
                        removed.add(file);
                    }
                }
            }
        }
    }

    private void createParent(File file) throws FileNotFoundException {
        File parentFile = file.getParentFile();
        if (!parentFile.mkdirs() && !parentFile.exists()) {
            throw new FileNotFoundException("Can't create the folder " + parentFile.getAbsolutePath());
        }
    }

    private class GitRevisionGetter implements RevisionGetter {
        @Override
        public Object getRevision() {
            try {
                return getLastRevision();
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
                return null;
            }
        }
    }

    public interface WalkCommand<T> {
        T apply(org.eclipse.jgit.lib.Repository repository, TreeWalk rootWalk, String baseFolder) throws
                                                                                                  IOException,
                                                                                                  GitAPIException;
    }

    public interface HistoryVisitor<T> {
        /**
         * Visit commit for a file with a path {@code fullPath}
         *
         * @param fullPath full path to the file
         * @param commit visiting commit
         * @param tagRefForCommit tag reference for commit
         * @return true if we should stop iterating history (we found needed information) and false if not found or
         * should iterate all commits
         */
        boolean visit(String fullPath, RevCommit commit, Ref tagRefForCommit) throws IOException, GitAPIException;

        /**
         * Get accumulated result
         */
        T getResult();
    }

    private class ListCommand implements WalkCommand<List<FileData>> {
        @Override
        public List<FileData> apply(org.eclipse.jgit.lib.Repository repository,
                TreeWalk rootWalk,
                String baseFolder) throws IOException, GitAPIException {
            if (rootWalk != null) {
                // Iterate files in folder
                List<FileData> files = new ArrayList<>();
                if (rootWalk.getFilter() == TreeFilter.ALL) {
                    while (rootWalk.next()) {
                        files.add(createFileData(repository, rootWalk, baseFolder));
                    }
                } else {
                    if (rootWalk.getTreeCount() > 0) {
                        try (TreeWalk dirWalk = new TreeWalk(repository)) {
                            dirWalk.addTree(rootWalk.getObjectId(0));
                            dirWalk.setRecursive(true);

                            while (dirWalk.next()) {
                                files.add(createFileData(repository, dirWalk, baseFolder));
                            }
                        }
                    }
                }

                return files;
            } else {
                return Collections.emptyList();
            }
        }
    }

    private class ListFoldersCommand implements WalkCommand<List<FileData>> {
        @Override
        public List<FileData> apply(org.eclipse.jgit.lib.Repository repository,
                TreeWalk rootWalk,
                String baseFolder) throws IOException, GitAPIException {
            if (rootWalk != null) {
                if (rootWalk.getFilter() == TreeFilter.ALL) {
                    return collectFolderData(rootWalk, baseFolder);
                } else {
                    if (rootWalk.getTreeCount() > 0) {
                        try (TreeWalk dirWalk = new TreeWalk(repository)) {
                            dirWalk.addTree(rootWalk.getObjectId(0));
                            return collectFolderData(dirWalk, baseFolder);
                        }
                    }
                }
            }

            return Collections.emptyList();
        }

        private List<FileData> collectFolderData(TreeWalk rootWalk, String baseFolder) throws IOException, GitAPIException {
            List<FileData> files = new ArrayList<>();
            rootWalk.setRecursive(false);
            while (rootWalk.next()) {
                if ((rootWalk.getFileMode().getBits() & FileMode.TYPE_TREE) != 0) {
                    files.add(createFolderData(rootWalk, baseFolder));
                }
            }

            return files;
        }
    }

    private class CheckCommand implements WalkCommand<FileData> {
        @Override
        public FileData apply(org.eclipse.jgit.lib.Repository repository, TreeWalk rootWalk, String baseFolder) throws
                                                                                                                IOException,
                                                                                                                GitAPIException {
            if (rootWalk != null) {
                return createFileData(repository, rootWalk, "");
            } else {
                return null;
            }
        }
    }

    private class ReadCommand implements WalkCommand<FileItem> {
        @Override
        public FileItem apply(org.eclipse.jgit.lib.Repository repository, TreeWalk rootWalk, String baseFolder) throws
                                                                                                                IOException,
                                                                                                                GitAPIException {
            if (rootWalk != null) {
                FileData fileData = createFileData(repository, rootWalk, "");
                ObjectLoader loader = repository.open(rootWalk.getObjectId(0));
                return new FileItem(fileData, loader.openStream());
            } else {
                return null;
            }
        }
    }

    private class ListHistoryVisitor implements HistoryVisitor<List<FileData>> {
        private final org.eclipse.jgit.lib.Repository repository;
        private final List<FileData> history = new ArrayList<>();

        private ListHistoryVisitor() {
            repository = git.getRepository();
        }

        @Override
        public boolean visit(String fullPath, RevCommit commit, Ref tagRefForCommit) throws IOException, GitAPIException {
            RevTree tree = commit.getTree();

            try (TreeWalk rootWalk = buildTreeWalk(repository, fullPath, tree)) {
                history.add(createFileData(repository, rootWalk, "", commit));
            }

            return false;
        }

        @Override
        public List<FileData> getResult() {
            Collections.reverse(history);
            return history;
        }
    }

    private class CheckHistoryVisitor implements HistoryVisitor<FileData> {
        private final String version;
        private final org.eclipse.jgit.lib.Repository repository;
        private FileData result;

        private CheckHistoryVisitor(String version) {
            this.version = version;
            repository = git.getRepository();
        }

        @Override
        public boolean visit(String fullPath, RevCommit commit, Ref tagRefForCommit) throws IOException, GitAPIException {
            if (getLocalTagName(tagRefForCommit).equals(version)) {
                RevTree tree = commit.getTree();

                try (TreeWalk rootWalk = buildTreeWalk(repository, fullPath, tree)) {
                    result = createFileData(repository, rootWalk, "", commit);
                    return true;
                }
            }

            return false;
        }

        @Override
        public FileData getResult() {
            return result;
        }
    }

    private class ReadHistoryVisitor implements HistoryVisitor<FileItem> {
        private final String version;
        private final org.eclipse.jgit.lib.Repository repository;
        private FileItem result;

        private ReadHistoryVisitor(String version) {
            this.version = version;
            repository = git.getRepository();
        }

        @Override
        public boolean visit(String fullPath, RevCommit commit, Ref tagRefForCommit) throws IOException, GitAPIException {
            if (getLocalTagName(tagRefForCommit).equals(version)) {
                RevTree tree = commit.getTree();

                try (TreeWalk rootWalk = buildTreeWalk(repository, fullPath, tree)) {
                    FileData fileData = createFileData(repository, rootWalk, "", commit);
                    ObjectLoader loader = repository.open(rootWalk.getObjectId(0));
                    result = new FileItem(fileData, loader.openStream());
                    return true;
                }
            }

            return false;
        }

        @Override
        public FileItem getResult() {
            return result;
        }
    }
}