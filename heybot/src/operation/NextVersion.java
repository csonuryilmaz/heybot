package operation;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Version;
import com.taskadapter.redmineapi.bean.VersionFactory;
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

	    // internal
	    int versionId = getParameterInt(prop, PARAMETER_VERSION_ID, 0);

	    redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);

	    int projectId = tryGetProjectId(redmineManager, filterProject);
	    if (projectId > 0)
	    {
		Issue[] issues = getReadyUnversionedIssues(redmineManager, filterProject, filterQuery);
		if (issues.length > 0)
		{
		    Version version;

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

		createSubversionTag(prop, redmineManager, versionId);
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
	    System.out.println("#" + issue.getId() + " " + issue.getSubject());

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
		createSubversionTag(getVersion(redmineManager, versionId), repositoryPath, trunkPath, tagsPath);
	    }
	    else
	    {
		System.err.println("Ooops! Create SVN tag is enabled but other helper parameters are empty. Plase check them.");
	    }
	}
    }

    private void createSubversionTag(Version version, String repositoryPath, String trunkPath, String tagsPath)
    {
	if (version != null)
	{
	    createSubversionTag(version.getName(), repositoryPath, trunkPath, tagsPath);
	}
	else
	{
	    System.err.println("Ooops! Create SVN tag is enabled but couldn't get version from redmine.");
	}
    }

    private void createSubversionTag(String versionName, String repositoryPath, String trunkPath, String tagsPath)
    {
	String svnCommand = tryExecute("which svn");
	if (svnCommand.length() > 0)
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
	else
	{
	    System.err.println("Ooops! Create SVN tag is enabled but couldn't find SVN command.");
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

}
