package operation;

import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import org.apache.commons.io.FileUtils;
import utilities.Properties;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/**
 * Operation: cleanup
 *
 * @author onuryilmaz
 */
public class Cleanup extends Operation
{

    private final Locale trLocale = new Locale("tr-TR");

    //<editor-fold defaultstate="collapsed" desc="parameters">
    //mandatory
    private final static String PARAMETER_WORKSPACE_PATH = "WORKSPACE_PATH";
    private final static String PARAMETER_REDMINE_STATUS = "REDMINE_STATUS";
    private final static String PARAMETER_REDMINE_TOKEN = "REDMINE_TOKEN";
    private final static String PARAMETER_REDMINE_URL = "REDMINE_URL";
    // optional
    private final static String PARAMETER_LIMIT = "LIMIT";
    private final static String PARAMETER_DELETE_WHEN_ISSUE_NOT_FOUND = "DELETE_WHEN_ISSUE_NOT_FOUND";

    //</editor-fold>
    public Cleanup() {
        super(new String[]
            {
                PARAMETER_WORKSPACE_PATH, PARAMETER_REDMINE_STATUS, PARAMETER_REDMINE_TOKEN, PARAMETER_REDMINE_URL
            }
        );
    }

    @Override
    public void execute(Properties prop) {
        if (areMandatoryParametersNotEmpty(prop)) {
            cleanWorkspace(prop);
        }
    }

    private void cleanWorkspace(Properties prop) {
        String workspacePath = getParameterString(prop, PARAMETER_WORKSPACE_PATH, false);
        if (!workspacePath.endsWith("/")) {
            workspacePath += "/";
        }
        HashSet<String> statuses = getParameterStringHash(prop, PARAMETER_REDMINE_STATUS, true);
        String redmineAccessToken = getParameterString(prop, PARAMETER_REDMINE_TOKEN, false);
        String redmineUrl = getParameterString(prop, PARAMETER_REDMINE_URL, false);

        boolean isDeleteEnabledWhenNotFound = getParameterBoolean(prop, PARAMETER_DELETE_WHEN_ISSUE_NOT_FOUND);
        int max = getParameterInt(prop, PARAMETER_LIMIT, Integer.MAX_VALUE);

        RedmineManager redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);

        Branch[] branches = getBranches(workspacePath);
        for (Branch branch : branches) {
            Issue issue = getIssue(redmineManager, branch.getIssueId());
            if (issue == null) {
                System.out.println("[w] #" + branch.getIssueId() + " not found on Redmine!");
                if (isDeleteEnabledWhenNotFound) {
                    deleteBranch(workspacePath, branch);
                    max--;
                }
            } else {
                System.out.println("[i] #" + issue.getId() + " - " + issue.getSubject());
                System.out.println("\t |_> " + branch.getDirectory() + "/");
                System.out.println("\t |_> " + issue.getStatusName());
                if (statuses.contains(issue.getStatusName().toLowerCase(trLocale))) {
                    deleteBranch(workspacePath, branch);
                    max--;
                }
            }

            if (max == 0) {
                break; // limit is reached!
            }
        }
    }

    private Branch[] getBranches(String dir) {
        String[] directories = new File(dir).list((current, name) -> new File(current, name).isDirectory());
        ArrayList<Branch> branches = new ArrayList<>();
        if (directories != null) {
            Branch branch;
            for (String directory : directories) {
                if ((branch = tryGetBranch(directory)) != null) {
                    branches.add(branch);
                }
            }
        }
        return branches.toArray(new Branch[0]);
    }

    private Branch tryGetBranch(String directory) {
        String issueId = directory.replaceAll("[^0-9]", "");
        if (issueId.length() > 0) {
            try {
                return new Branch(Integer.parseInt(issueId), directory);
            } catch (NumberFormatException nfe) {
                // ignore, null will be returned
            }
        }

        return null;
    }

    private void deleteBranch(String workspacePath, Branch branch) {
        File path = new File(workspacePath + branch.getDirectory());
        System.out.println("[*] Deleting: " + path.getAbsolutePath());
        try {
            FileUtils.deleteDirectory(path);
            System.out.println("[✓] Deleted.");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("[e] " + e.getMessage());
        }
    }

    class Branch
    {

        private final int issueId;
        private final String directory;

        Branch(int issueId, String directory) {
            this.issueId = issueId;
            this.directory = directory;
        }

        int getIssueId() {
            return issueId;
        }

        String getDirectory() {
            return directory;
        }
    }

}
