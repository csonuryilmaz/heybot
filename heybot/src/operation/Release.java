package operation;

import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Version;
import utilities.Properties;

/**
 *
 * @author onur
 */
public class Release extends Operation
{
//<editor-fold defaultstate="collapsed" desc="parameters">

    // mandatory
    private final static String PARAMETER_VERSION_ID = "VERSION_ID";
    private final static String PARAMETER_REDMINE_TOKEN = "REDMINE_TOKEN";
    private final static String PARAMETER_REDMINE_URL = "REDMINE_URL";

    // optional
    private final static String PARAMETER_DEPLOY_CMD = "DEPLOY_CMD";
    private final static String PARAMETER_SUCCESS_EMAIL = "SUCCESS_EMAIL";
    private final static String PARAMETER_FAILURE_EMAIL = "FAILURE_EMAIL";
    private final static String PARAMETER_SMTP_USERNAME = "SMTP_USERNAME";
    private final static String PARAMETER_SMTP_PASSWORD = "SMTP_PASSWORD";
    private final static String PARAMETER_SMTP_HOST = "SMTP_HOST";
    private final static String PARAMETER_SMTP_PORT = "SMTP_PORT";
    private final static String PARAMETER_SMTP_TLS_ENABLED = "SMTP_TLS_ENABLED";
    private final static String PARAMETER_SUCCESS_HOOK = "SUCCESS_HOOK";
    private final static String PARAMETER_FAILURE_HOOK = "FAILURE_HOOK";
    //</editor-fold>
    private RedmineManager redmineManager;

    public Release()
    {
	super(new String[]
	{
	    PARAMETER_REDMINE_TOKEN, PARAMETER_REDMINE_URL, PARAMETER_VERSION_ID
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
	    int versionId = getParameterInt(prop, PARAMETER_VERSION_ID, 0);

	    redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);

	    System.out.println("* Getting version from redmine: " + versionId);
	    Version version = getVersion(redmineManager, versionId);
	    if (version != null)
	    {
		release(version);
	    }
	}
    }

    private void release(Version version)
    {
	System.out.println("  [" + version.getName() + "]");
	Issue[] issues = getVersionIssues(redmineManager, version);

	if (!isVersionDeployed(version))
	{
	    deployVersion(version, issues);// inside managed exception on any error
	}
	else
	{
	    System.out.println("- [info] Version is already deployed!");
	}

	String changeLog = getChangeLog(version, issues);
	notifyEmail(version, changeLog);
	notifySlack(version, changeLog);
    }

    private boolean isVersionDeployed(Version version)
    {
	return version.getStatus().equals("closed");
    }

    private void deployVersion(Version version, Issue[] issues)
    {
	runDeployCommands(version);
	updateAsDeployed(issues);
	updateAsDeployed(version);
    }

    private void runDeployCommands(Version version)
    {
	System.out.println("* Running deployment commands ... ");
	// todo
    }

    private void updateAsDeployed(Issue[] issues)
    {
	System.out.println("* Updating related issues ... ");
	for (Issue issue : issues)
	{
	    System.out.println(issue.toString());
	    // todo
	}
	System.out.println("  Total " + issues.length + " issue(s) are updated as #todo .");
    }

    private void updateAsDeployed(Version version)
    {
	System.out.println("* Updating related version ... ");
    }

    private String getChangeLog(Version version, Issue[] issues)
    {
	System.out.println("* Generating changelog ... ");

	return null;
    }

    private void notifySlack(Version version, String changeLog)
    {
	System.out.println("* Sending slack notification ... ");
    }

    private void notifyEmail(Version version, String changeLog)
    {
	System.out.println("* Sending email notification ... ");
    }

}
