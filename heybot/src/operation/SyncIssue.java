package operation;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Properties;
import java.util.TimeZone;

/**
 *
 * @author onur
 */
public class SyncIssue extends Operation
{

    // mandatory
    private final static String PARAMETER_SUPPORT_PROJECT = "SUPPORT_PROJECT";
    private final static String PARAMETER_SUPPORT_NEW_STATUS = "SUPPORT_NEW_STATUS";
    private final static String PARAMETER_SUPPORT_IN_PROGRESS_STATUS = "SUPPORT_IN_PROGRESS_STATUS";
    private final static String PARAMETER_SUPPORT_ON_HOLD_STATUS = "SUPPORT_ON_HOLD_STATUS";
    private final static String PARAMETER_SUPPORT_CLOSED_STATUS = "SUPPORT_CLOSED_STATUS";
    private final static String PARAMETER_INTERNAL_NEW_STATUS = "INTERNAL_NEW_STATUS";
    private final static String PARAMETER_INTERNAL_IN_PROGRESS_STATUS = "INTERNAL_IN_PROGRESS_STATUS";
    private final static String PARAMETER_INTERNAL_ON_HOLD_STATUS = "INTERNAL_ON_HOLD_STATUS";
    private final static String PARAMETER_INTERNAL_CLOSED_STATUS = "INTERNAL_CLOSED_STATUS";
    private final static String PARAMETER_SUPPORT_WATCHER = "SUPPORT_WATCHER";
    private final static String PARAMETER_REDMINE_TOKEN = "REDMINE_TOKEN";
    private final static String PARAMETER_REDMINE_URL = "REDMINE_URL";
    // internal
    private final static String PARAMETER_LAST_CHECK_TIME = "LAST_CHECK_TIME";

    public SyncIssue()
    {
	super(new String[]
	{
	    PARAMETER_SUPPORT_PROJECT, PARAMETER_SUPPORT_NEW_STATUS, PARAMETER_SUPPORT_IN_PROGRESS_STATUS, PARAMETER_SUPPORT_ON_HOLD_STATUS, PARAMETER_SUPPORT_CLOSED_STATUS, PARAMETER_INTERNAL_NEW_STATUS, PARAMETER_INTERNAL_IN_PROGRESS_STATUS, PARAMETER_INTERNAL_ON_HOLD_STATUS, PARAMETER_INTERNAL_CLOSED_STATUS, PARAMETER_SUPPORT_WATCHER, PARAMETER_REDMINE_TOKEN, PARAMETER_REDMINE_URL
	}
	);
    }

    @Override
    public void execute(Properties prop)
    {
	if (areMandatoryParametersNotEmpty(prop))
	{
	    String[] supportProjects = getParameterStringArray(prop, PARAMETER_SUPPORT_PROJECT, true);

	    String supportNewStatus = getParameterString(prop, PARAMETER_SUPPORT_NEW_STATUS, true);
	    String supportInProgressStatus = getParameterString(prop, PARAMETER_SUPPORT_IN_PROGRESS_STATUS, true);
	    String supportOnHoldSatus = getParameterString(prop, PARAMETER_SUPPORT_ON_HOLD_STATUS, true);
	    String supportClosedStatus = getParameterString(prop, PARAMETER_SUPPORT_CLOSED_STATUS, true);

	    HashSet<String> internalNewStatuses = getParameterStringHash(prop, PARAMETER_INTERNAL_NEW_STATUS, true);
	    HashSet<String> internalInProgressStatuses = getParameterStringHash(prop, PARAMETER_INTERNAL_IN_PROGRESS_STATUS, true);
	    HashSet<String> internalOnHoldStatuses = getParameterStringHash(prop, PARAMETER_INTERNAL_ON_HOLD_STATUS, true);
	    HashSet<String> internalClosedStatuses = getParameterStringHash(prop, PARAMETER_INTERNAL_CLOSED_STATUS, true);

	    String[] supportWatchers = getParameterStringArray(prop, PARAMETER_SUPPORT_WATCHER, true);

	    String redmineAccessToken = getParameterString(prop, PARAMETER_REDMINE_TOKEN, false);
	    String redmineUrl = getParameterString(prop, PARAMETER_REDMINE_URL, false);
	    // internal
	    Date lastCheckTime = getParameterDateTime(prop, PARAMETER_LAST_CHECK_TIME);

	    System.out.println("Not implemented yet.");

	    setParameterDateTime(prop, PARAMETER_LAST_CHECK_TIME, new Date());
	}
    }

}
