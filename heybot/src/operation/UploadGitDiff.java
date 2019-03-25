package operation;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import utilities.Properties;

public class UploadGitDiff extends Operation
{

    // mandatory
    private final static String PARAMETER_HOST = "HOST";
    private final static String PARAMETER_USERNAME = "USERNAME";
    private final static String PARAMETER_PASSWORD = "PASSWORD";
    private final static String PARAMETER_REMOTE_PATH = "REMOTE_PATH";

    // optional
    private final static String PARAMETER_SOURCE_PATH = "SOURCE_PATH";

    private File sftpSourceDir;
    private Status status;

    public UploadGitDiff() {
	super(new String[]{
	    PARAMETER_HOST, PARAMETER_USERNAME, PARAMETER_PASSWORD, PARAMETER_REMOTE_PATH
	});
    }

    @Override
    protected void execute(Properties prop) throws Exception {
	if (areMandatoryParametersNotEmpty(prop)) {
	    setSftpSourceDir(prop);
	    if (!sftpSourceDir.exists()) {
		System.out.println("[e] Couldn't find or detect any existing SOURCE_PATH to upload!");
		return;
	    }
	    System.out.println("[i] Source Path: " + sftpSourceDir.getAbsolutePath());
	    if (hasLocalWorkingCopyChanges()) {
		System.out.println("@todo: Start upload!");
	    }
	}
    }

    private void setSftpSourceDir(Properties prop) {
	String sourcePath = getParameterString(prop, PARAMETER_SOURCE_PATH, false);
	if (StringUtils.isBlank(sourcePath)) {
	    sourcePath = System.getProperty("user.dir");
	}
	sftpSourceDir = new File(sourcePath);
    }

    private boolean hasLocalWorkingCopyChanges() {
	System.out.println("[*] Checking local working copy changes ...");
	try (Repository gitRepo = openRepository(sftpSourceDir.getAbsolutePath())) {
	    try (Git git = new Git(gitRepo)) {
		status = git.status().call();
		if (status.isClean()) {
		    System.out.println("\t[i] Nothing to upload, working tree clean.");
		} else {
		    if (status.getConflicting().size() > 0) {
			listFiles("Conflicting", status.getConflicting());
			System.out.println("\t[e] Please, resolve conflicts before upload.");
		    } else {
			int count = 0;
			count += listFiles("Added", status.getAdded());
			count += listFiles("Changed", status.getChanged());
			count += listFiles("Missing", status.getMissing());
			count += listFiles("Modified", status.getModified());
			count += listFiles("Removed", status.getRemoved());
			count += listFiles("Untracked", status.getUntracked());
			count += listFiles("Untracked Folders", status.getUntrackedFolders());
			System.out.println("Total changes: " + count);
			return count > 0;
		    }
		}
	    } catch (GitAPIException ex) {
		System.out.println("\t[e] Git status failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
	    }
	} catch (IOException ex) {
	    System.out.println("\t[e] Git status failed! " + ex.getClass().getCanonicalName() + " " + ex.getMessage());
	}
	return false;
    }

    private Repository openRepository(String path) throws IOException {
	FileRepositoryBuilder builder = new FileRepositoryBuilder();
	builder.setGitDir(new File(path + "/.git"));
	builder.setMustExist(true);
	return builder.readEnvironment().build();
    }

    private int listFiles(String title, Set<String> files) {
	if (files.size() > 0) {
	    System.out.println("\t" + title + ": " + files.size());
	    files.forEach((file) -> {
		System.out.println("\t\t" + file);
	    });
	}
	return files.size();
    }

}
