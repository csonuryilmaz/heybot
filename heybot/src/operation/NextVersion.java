package operation;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Version;
import com.taskadapter.redmineapi.bean.VersionFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;
import model.VersionTag;
import static org.apache.http.util.TextUtils.isEmpty;

/**
 *
 * @author onuryilmaz
 */
public class NextVersion extends Operation
{
    //<editor-fold defaultstate="collapsed" desc="parameters">

    // mandatory
    private final static String PARAMETER_REDMINE_TOKEN = "REDMINE_TOKEN";
    private final static String PARAMETER_REDMINE_URL = "REDMINE_URL";
    private final static String PARAMETER_FILTER_PROJECT = "FILTER_PROJECT";
    private final static String PARAMETER_FILTER_QUERY = "FILTER_QUERY";
    private final static String PARAMETER_VERSION_TITLE = "VERSION_TITLE";
    private final static String PARAMETER_MAJOR_TRACKER = "MAJOR_TRACKER";
    private final static String PARAMETER_MINOR_TRACKER = "MINOR_TRACKER";
    private final static String PARAMETER_PATCH_TRACKER = "PATCH_TRACKER";
    // optional
    private final static String PARAMETER_CLOSE_PREVIOUS = "CLOSE_PREVIOUS";
    private final static String PARAMETER_APPEND_CURRENT = "APPEND_CURRENT";
    private final static String PARAMETER_CREATE_SVN_TAG = "CREATE_SVN_TAG";
    private final static String PARAMETER_REPOSITORY_PATH = "REPOSITORY_PATH";
    private final static String PARAMETER_TRUNK_PATH = "TRUNK_PATH";
    private final static String PARAMETER_TAGS_PATH = "TAGS_PATH";
    private final static String PARAMETER_APP_VERSION_FILE_PATH = "APP_VERSION_FILE_PATH";
    private final static String PARAMETER_APP_VERSION_FILE_PATTERN = "APP_VERSION_FILE_PATTERN";
    // internal
    private final static String PARAMETER_VERSION_TAG = "VERSION_TAG";
    private final static String PARAMETER_PREVIOUS_VERSION_TAG = "PREVIOUS_VERSION_TAG";
    private final static String PARAMETER_VERSION_ID = "VERSION_ID";

    //</editor-fold>
    private RedmineManager redmineManager;

    public NextVersion()
    {
	super(new String[]
	{
	    PARAMETER_REDMINE_TOKEN, PARAMETER_REDMINE_URL, PARAMETER_FILTER_PROJECT, PARAMETER_FILTER_QUERY, PARAMETER_VERSION_TITLE, PARAMETER_MAJOR_TRACKER, PARAMETER_MINOR_TRACKER, PARAMETER_PATCH_TRACKER
	}
	);
    }

    @Override
    public void execute(Properties prop) throws Exception
    {
	if (areMandatoryParametersNotEmpty(prop))
	{
	    String redmineAccessToken = getParameterString(prop, PARAMETER_REDMINE_TOKEN, false);
	    String redmineUrl = getParameterString(prop, PARAMETER_REDMINE_URL, false);
	    String filterProject = getParameterString(prop, PARAMETER_FILTER_PROJECT, true);
	    String filterQuery = getParameterString(prop, PARAMETER_FILTER_QUERY, true);
	    String versionTitle = getParameterString(prop, PARAMETER_VERSION_TITLE, false);

	    boolean closePreviousVersion = getParameterBoolean(prop, PARAMETER_CLOSE_PREVIOUS);
	    boolean appendCurrentVersion = getParameterBoolean(prop, PARAMETER_APPEND_CURRENT);

	    redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);

	    int projectId = tryGetProjectId(redmineManager, filterProject);
	    if (projectId > 0)
	    {
		Issue[] issues = getReadyUnversionedIssues(redmineManager, filterProject, filterQuery);
		if (issues.length > 0)
		{
		    Version version;
		    int versionId = getParameterInt(prop, PARAMETER_VERSION_ID, 0);

		    if (appendCurrentVersion)
		    {
			version = appendVersion(redmineManager, prop, issues, versionId, versionTitle);
		    }
		    else
		    {
			if (closePreviousVersion)
			{
			    closeVersion(redmineManager, versionId);
			}

			version = createVersion(redmineManager, prop, issues, projectId, versionTitle);
		    }

		    assignTargetVersion(redmineManager, issues, version);
		}

		createSubversionTag(prop, redmineManager, getParameterInt(prop, PARAMETER_VERSION_ID, 0));
	    }
	    else
	    {
		System.err.println("Ooops! Couldn't find project. Next-version operation works only on valid redmine project.");
	    }
	}
    }

    private void closeVersion(RedmineManager redmineManager, int versionId)
    {
	if (versionId > 0)
	{
	    try
	    {
		System.out.println("Getting version from redmine: " + versionId);
		Version version = redmineManager.getProjectManager().getVersionById(versionId);
		if (!version.getStatus().equals("closed"))
		{
		    version.setStatus("closed");
		    redmineManager.getProjectManager().update(version);
		    System.out.println("Version [" + version.getName() + "] is updated as closed.");
		}
		else
		{
		    System.out.println("Version [" + version.getName() + "] is already closed.");
		}
	    }
	    catch (RedmineException ex)
	    {
		System.err.println("Ooops! Couldn't complete closing last version.(" + ex.getMessage() + ")");
	    }
	}
    }

    private Version createVersion(RedmineManager redmineManager, int projectId, String versionTitle, String versionTag)
    {
	String versionName = versionTitle + "-" + versionTag;
	try
	{
	    System.out.println("Creating new redmine version: [" + versionName + "]");
	    Version version = VersionFactory.create(projectId, versionName);
	    version.setStatus("open");

	    version = redmineManager.getProjectManager().createVersion(version);
	    System.out.println("[✓] VERSION_ID=" + version.getId());

	    return version;
	}
	catch (RedmineException ex)
	{
	    System.err.println("Ooops! Can't create new version. (" + ex.getMessage() + ")");
	}

	return null;
    }

    private Issue[] getReadyUnversionedIssues(RedmineManager redmineManager, String filterProject, String filterQuery)
    {
	int filterSavedQueryId = tryGetSavedQueryId(redmineManager, filterProject, filterQuery);
	if (filterSavedQueryId > 0)
	{
	    Issue[] issues = getProjectIssues(redmineManager, filterProject, filterSavedQueryId);
	    System.out.println("Ready to release and unversioned " + issues.length + " issue(s) found.");

	    return issues;
	}
	else
	{
	    System.err.println("Ooops! Couldn't find saved query. Saved query contains ready and unversioned issues.");
	}

	return new Issue[0];
    }

    private void assignTargetVersion(RedmineManager redmineManager, Issue[] issues, Version version)
    {
	for (Issue issue : issues)
	{
	    System.out.println("#" + issue.getId() + " [" + issue.getTracker().getName() + "]" + " " + issue.getSubject());

	    issue.setTargetVersion(version);
	    try
	    {
		redmineManager.getIssueManager().update(issue);
		System.out.println("[✓] Target Version: [" + version.getName() + "]");
	    }
	    catch (RedmineException ex)
	    {
		System.err.println("Ooops! Can't assign target version. (" + ex.getMessage() + ")");
	    }
	}
    }

    private boolean updateVersion(RedmineManager redmineManager, Version version, String versionTitle, String versionTag)
    {
	String versionName = versionTitle + "-" + versionTag;
	try
	{
	    System.out.println("Updating redmine version: [" + version.getName() + "] -> [" + versionName + "]");
	    version.setName(versionName);

	    redmineManager.getProjectManager().update(version);
	    System.out.println("[✓] VERSION_ID=" + version.getId());

	    return true;
	}
	catch (RedmineException ex)
	{
	    System.err.println("Ooops! Can't update existing version. (" + ex.getMessage() + ")");
	}

	return false;
    }

    private Version createVersion(RedmineManager redmineManager, Properties prop, Issue[] issues, int projectId, String versionTitle) throws Exception
    {
	VersionTag versionTag = new VersionTag(getParameterString(prop, PARAMETER_VERSION_TAG, false),
		getParameterStringHash(prop, PARAMETER_MAJOR_TRACKER, true),
		getParameterStringHash(prop, PARAMETER_MINOR_TRACKER, true),
		getParameterStringHash(prop, PARAMETER_PATCH_TRACKER, true));

	versionTag.next(issues);

	Version version = createVersion(redmineManager, projectId, versionTitle, versionTag.toString());
	if (version != null)
	{
	    setParameterString(prop, PARAMETER_PREVIOUS_VERSION_TAG, getParameterString(prop, PARAMETER_VERSION_TAG, false));
	    setParameterString(prop, PARAMETER_VERSION_TAG, versionTag.toString());
	    setParameterInt(prop, PARAMETER_VERSION_ID, version.getId());
	}

	return version;
    }

    private Version appendVersion(RedmineManager redmineManager, Properties prop, Issue[] issues, int versionId, String versionTitle) throws Exception
    {
	VersionTag versionTag = new VersionTag(getParameterString(prop, PARAMETER_VERSION_TAG, false),
		getParameterString(prop, PARAMETER_PREVIOUS_VERSION_TAG, false),
		getParameterStringHash(prop, PARAMETER_MAJOR_TRACKER, true),
		getParameterStringHash(prop, PARAMETER_MINOR_TRACKER, true),
		getParameterStringHash(prop, PARAMETER_PATCH_TRACKER, true));

	versionTag.next(issues);

	Version version = getVersion(redmineManager, versionId);
	if (version != null && updateVersion(redmineManager, version, versionTitle, versionTag.toString()))
	{
	    setParameterString(prop, PARAMETER_VERSION_TAG, versionTag.toString());
	}

	return version;
    }

    private void createSubversionTag(Properties prop, RedmineManager redmineManager, int versionId)
    {
	boolean isCreateSubversionTagEnabled = getParameterBoolean(prop, PARAMETER_CREATE_SVN_TAG);
	if (isCreateSubversionTagEnabled)
	{
	    String repositoryPath = trimRight(getParameterString(prop, PARAMETER_REPOSITORY_PATH, false), "/");
	    String trunkPath = trimLeft(trimRight(getParameterString(prop, PARAMETER_TRUNK_PATH, false), "/"), "/");
	    String tagsPath = trimLeft(trimRight(getParameterString(prop, PARAMETER_TAGS_PATH, false), "/"), "/");

	    if (!isEmpty(repositoryPath) && !isEmpty(trunkPath) && !isEmpty(tagsPath))
	    {
		String svnCommand = tryExecute("which svn");
		if (svnCommand.length() > 0)
		{
		    createSubversionTag(svnCommand, getVersion(redmineManager, versionId), repositoryPath, trunkPath, tagsPath,
			    getParameterStringArray(prop, PARAMETER_APP_VERSION_FILE_PATH, false),
			    getParameterString(prop, PARAMETER_APP_VERSION_FILE_PATTERN, false));
		}
		else
		{
		    System.err.println("Ooops! Create SVN tag is enabled but couldn't find SVN command.");
		}
	    }
	    else
	    {
		System.err.println("Ooops! Create SVN tag is enabled but other helper parameters are empty. Plase check them.");
	    }
	}
    }

    private void createSubversionTag(String svnCommand, Version version, String repositoryPath, String trunkPath, String tagsPath, String[] appVersionFilePaths, String appVersionFilePattern)
    {
	if (version != null)
	{
	    if (appVersionFilePaths.length == 0 || updateAppVersionFile(svnCommand, version.getName(), repositoryPath, trunkPath, appVersionFilePaths, appVersionFilePattern))
	    {
		createSubversionTag(svnCommand, version.getName(), repositoryPath, trunkPath, tagsPath);
	    }
	    else
	    {
		System.err.println("Ooops! Couldn't update app version files successfully, so no version tag is created.");
	    }
	}
	else
	{
	    System.err.println("Ooops! Create SVN tag is enabled but couldn't get version from redmine.");
	}
    }

    private void createSubversionTag(String svnCommand, String versionName, String repositoryPath, String trunkPath, String tagsPath)
    {
	String tagPath = repositoryPath + "/" + tagsPath + "/" + versionName;
	String srcPath = repositoryPath + "/" + trunkPath;
	if (!isSvnPathExists(svnCommand, tagPath))
	{
	    if (isSvnPathExists(svnCommand, srcPath))
	    {
		if (createSvnTag(svnCommand, srcPath, tagPath, versionName))
		{
		    System.out.println("[✓] ^/" + tagsPath + "/" + versionName + " created successfully.");
		}
	    }
	    else
	    {
		System.err.println("Ooops! Create SVN tag is enabled but couldn't find TRUNK in repository. (" + srcPath + ")");
	    }
	}
	else
	{
	    System.out.println("Tag already exists in ^/" + tagsPath + " folder for current version [" + versionName + "].");
	}
    }

    private boolean isSvnPathExists(String svnCommand, String tagPath)
    {
	String[] output = execute(svnCommand + " ls " + tagPath + " --depth empty");
	return output == null || (output[1].length() == 0 && output[0].length() == 0);
    }

    private boolean createSvnTag(String svnCommand, String srcPath, String tagPath, String comment)
    {
	String[] output = execute(svnCommand + " copy " + srcPath + " " + tagPath + " -m " + comment);

	if (output != null)
	{
	    if (output[1].length() == 0)
	    {
		System.out.println(output[0]);
		return true;
	    }
	    else
	    {
		System.err.println(output[1]);
	    }
	}

	return false;
    }

    private boolean updateAppVersionFile(String svnCommand, String versionName, String repositoryPath, String trunkPath, String[] appVersionFilePaths, String appVersionFilePattern)
    {
	appVersionFilePaths = trimLeft(appVersionFilePaths, "/");

	String localPath = getWorkingDirectory() + "/" + "tmp";
	createFolder(localPath);

	localPath += "/" + trunkPath;
	repositoryPath += "/" + trunkPath;

	System.out.println("=== " + "Getting app version files into " + localPath);
	if (delete(new File(localPath)) && svnCheckout(svnCommand, repositoryPath, localPath, true))
	{
	    svnCheckout(svnCommand, localPath, appVersionFilePaths);
	    if (updateAppVersionFile(localPath, appVersionFilePaths, appVersionFilePattern, versionName)
		    && svnCommit(svnCommand, localPath, "Version tag is modified for release: " + versionName))
	    {
		return true;
	    }
	}
	else
	{
	    System.err.println("Ooops! Checkout " + trunkPath + " could not be done!");
	}

	return false;
    }

    private boolean svnCheckout(String svnCommand, String trunkPath, String localPath, boolean isDepthEmpty)
    {
	String command = svnCommand + " co " + trunkPath + " " + localPath;
	if (isDepthEmpty)
	{
	    command += " --depth empty ";
	}
	System.out.println(command);
	String[] output = execute(command);
	if (output == null || output[1].length() > 0)
	{
	    System.err.println(output[1]);
	    return false;
	}

	System.out.println(output[0]);
	return true;
    }

    private void createFolder(String folderName)
    {
	File folder = new File(folderName);
	if (!folder.exists())
	{
	    folder.mkdir();
	}
    }

    private void svnCheckout(String svnCommand, String localPath, String[] filePaths)
    {
	for (String filePath : filePaths)
	{
	    String[] tokens = filePath.split("/");
	    if (tokens.length == 1)
	    {
		svnUpdate(svnCommand, localPath + "/" + tokens[0], false);
	    }
	    else
	    {
		int i = 0;
		String buffer = "";
		for (; i < tokens.length - 1; i++)
		{
		    svnUpdate(svnCommand, localPath + "/" + tokens[i], true);
		    buffer += tokens[i] + "/";
		}
		svnUpdate(svnCommand, localPath + "/" + buffer + tokens[i], false);
	    }
	}
    }

    private boolean svnUpdate(String svnCommand, String filePath, boolean isDepthEmpty)
    {
	String command = svnCommand + " up " + filePath;
	if (isDepthEmpty)
	{
	    command += " --depth empty";
	}
	System.out.println(command);
	String[] output = execute(command);
	if (output == null || output[1].length() > 0)
	{
	    System.err.println(output[1]);
	    return false;
	}

	System.out.println(output[0]);
	return true;
    }

    private boolean svnCommit(String svnCommand, String workingDirPath, String comment)
    {
	String[] command = new String[]
	{
	    svnCommand, "commit", workingDirPath, "-m", comment
	};
	System.out.println(svnCommand + " commit " + workingDirPath + " -m \"" + comment + "\"");
	String[] output = execute(command);
	if (output == null || output[1].length() > 0)
	{
	    System.err.println(output[1]);
	    return false;
	}

	System.out.println(output[0]);
	return true;
    }

    private boolean updateAppVersionFile(String localPath, String[] appVersionFilePaths, String appVersionFilePattern, String versionName)
    {
	String versionTag = getVersionTag(versionName);
	if (isEmpty(versionTag))
	{
	    System.err.println("Ooops! Version tag is not recognized from version name. (" + versionName + ")");
	    return false;
	}

	String[] pattern = appVersionFilePattern.split("<>");
	if (pattern.length != 3 || !pattern[1].equals("VERSION_TAG"))
	{
	    System.err.println("Ooops! App version file pattern is not recognized. (" + appVersionFilePattern + ")");
	    return false;
	}
	String headPattern = pattern[0];
	String tailPattern = pattern[2];

	for (String appVersionFilePath : appVersionFilePaths)
	{
	    if (!replaceInFile(localPath + "/" + appVersionFilePath, headPattern, versionTag, tailPattern))
	    {
		System.err.println("Ooops! Replacing version tag in app file could not be done.");
		return false;
	    }
	}

	return true;
    }

    private boolean replaceInFile(String filePath, String headPattern, String versionTag, String tailPattern)
    {
	System.out.println("=== Replacing app version file " + filePath);
	File in = new File(filePath);
	if (!in.exists())
	{
	    System.err.println("Ooops! The input file " + in + " does not exist!");
	    return false;
	}
	File out = new File(filePath + "_tmp");
	if (out.exists())
	{
	    System.err.println("Ooops! The output file " + out + " already exists!");
	    return false;
	}

	try
	{
	    BufferedReader reader = new BufferedReader(new FileReader(in));
	    PrintWriter writer = new PrintWriter(new FileWriter(out));

	    String line;
	    while ((line = reader.readLine()) != null)
	    {
		writer.println(replaceInLine(line, headPattern, versionTag, tailPattern));
	    }

	    reader.close();
	    writer.close();

	    in.delete();
	    out.renameTo(in);

	    return true;
	}
	catch (FileNotFoundException ex)
	{
	    System.err.println(ex.getMessage());
	}
	catch (IOException ex)
	{
	    System.err.println(ex.getMessage());
	}

	return false;
    }

    private String replaceInLine(String line, String headPattern, String versionTag, String tailPattern)
    {
	int index = line.indexOf(headPattern);
	if (index >= 0)
	{
	    index += headPattern.length();

	    String temp = line.substring(0, index);
	    temp += versionTag;

	    index = line.indexOf(tailPattern);
	    if (index >= 0)
	    {
		temp += line.substring(index);

		System.out.println("\n line (->): " + line);
		System.out.println("\t is replaced with");
		System.out.println(" line (<-): " + temp + "\n");

		return temp;
	    }
	}

	return line;
    }

    private boolean delete(File path)
    {
	if (!path.exists())
	{
	    return true;
	}

	if (path.isDirectory())
	{
	    for (File child : path.listFiles())
	    {
		delete(child);
	    }
	}

	return path.delete();
    }

    private String getVersionTag(String versionName)
    {
	String[] tokens = versionName.trim().split("-");

	if (tokens.length >= 2)
	{
	    return tokens[1];
	}
	else if (tokens.length == 1)
	{
	    return tokens[0];
	}

	return "";
    }

}
