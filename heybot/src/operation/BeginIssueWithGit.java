package operation;

import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import utilities.Properties;

public class BeginIssueWithGit extends Operation {

    // mandatory
    private final static String PARAMETER_REDMINE_URL = "REDMINE_URL";
    private final static String PARAMETER_REDMINE_TOKEN = "REDMINE_TOKEN";

    private final static String PARAMETER_ISSUE = "ISSUE";
    private final static String PARAMETER_REPOSITORY_PATH = "REPOSITORY_PATH";

    // optional
    private final static String PARAMETER_BEGAN_STATUS = "BEGAN_STATUS";
    private final static String PARAMETER_ASSIGNEE_ID = "ASSIGNEE_ID";

    private final static String PARAMETER_WORKSPACE_PATH = "WORKSPACE_PATH";
    private final static String PARAMETER_IDE_PATH = "IDE_PATH";

    private final static String PARAMETER_REMOTE_HOST = "REMOTE_HOST";
    private final static String PARAMETER_REMOTE_USER = "REMOTE_USER";
    private final static String PARAMETER_REMOTE_PASS = "REMOTE_PASS";
    private final static String PARAMETER_REMOTE_EXEC = "REMOTE_EXEC";
    private final static String PARAMETER_REMOTE_PORT = "REMOTE_PORT";

    private RedmineManager redmineManager;

    public BeginIssueWithGit() {
        super(new String[]{
            PARAMETER_ISSUE, PARAMETER_REDMINE_TOKEN, PARAMETER_REDMINE_URL, PARAMETER_REPOSITORY_PATH
        });
    }

    @Override
    protected void execute(Properties prop) throws Exception {
        if (areMandatoryParametersNotEmpty(prop)) {
            String redmineAccessToken = getParameterString(prop, PARAMETER_REDMINE_TOKEN, false);
            String redmineUrl = getParameterString(prop, PARAMETER_REDMINE_URL, false);
            redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);

            Issue issue = getIssue(redmineManager, getParameterInt(prop, PARAMETER_ISSUE, 0));
            if (issue != null) {
                System.out.println("#" + issue.getId() + " - " + issue.getSubject());
                if (isIssueAssignedTo(issue, prop)) {
                    System.out.println("continue");
                }
            }
        }
    }
    
    private boolean isIssueAssignedTo(Issue issue, Properties prop) {
        int assigneeId = getParameterInt(prop, PARAMETER_ASSIGNEE_ID, 0);
        System.out.println("[i] #" + issue.getId() + " is assigned to " + issue.getAssigneeName() + ".");
        if (assigneeId > 0) {
            if (issue.getAssigneeId() != assigneeId) {
                System.out.println("[e] Assignee check is failed!");
                System.out.println("[i] (suggested option!) Change assignee of the issue to yourself if you're sure to begin this issue.");
                System.out.println("[i] (not suggested but) You can comment " + PARAMETER_ASSIGNEE_ID + " from config file to disable assignee check.");
                return false;
            } else {
                System.out.println("[✓] Assignee check is successful.");
            }
        } else {
            System.out.println("[i] Assignee check is disabled.");
        }
        return true;
    }
}
