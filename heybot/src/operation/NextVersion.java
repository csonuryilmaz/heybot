package operation;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Version;
import com.taskadapter.redmineapi.bean.VersionFactory;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import model.VersionTag;

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
    // internal
    private final static String PARAMETER_VERSION_TAG = "VERSION_TAG";
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

	    if (appendCurrentVersion)
	    {
		closePreviousVersion = false;
	    }
	    // internal
	    int versionId = getParameterInt(prop, PARAMETER_VERSION_ID, 0);

	    redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);

	    int projectId = tryGetProjectId(redmineManager, filterProject);
	    if (projectId > 0)
	    {
		if (closePreviousVersion)
		{
		    closeVersion(redmineManager, versionId);
		}

		Issue[] issues = getReadyUnversionedIssues(redmineManager, filterProject, filterQuery);

		VersionTag versionTag = new VersionTag(getParameterString(prop, PARAMETER_VERSION_TAG, false),
			getParameterStringHash(prop, PARAMETER_MAJOR_TRACKER, true),
			getParameterStringHash(prop, PARAMETER_MINOR_TRACKER, true),
			getParameterStringHash(prop, PARAMETER_PATCH_TRACKER, true));
		versionTag.next(issues);

		Version version;
		if (!appendCurrentVersion)
		{
		    version = createVersion(redmineManager, projectId, versionTitle, versionTag.toString());
		    if (version != null)
		    {
			setParameterString(prop, PARAMETER_VERSION_TAG, versionTag.toString());
			setParameterInt(prop, PARAMETER_VERSION_ID, version.getId());
		    }
		}
		else
		{
		    version = getVersion(redmineManager, versionId);
		}

		assignTargetVersion(redmineManager, issues, version);

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

}
