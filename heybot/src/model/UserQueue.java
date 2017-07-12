package model;

import java.util.ArrayList;

/**
 *
 * @author onuryilmaz
 */
public class UserQueue
{

    private final int userId;
    private final ArrayList<String> assigneeOfIssues = new ArrayList();
    private final ArrayList<String> secondaryAssigneeOfIssues = new ArrayList();

    public UserQueue(int userId)
    {
	this.userId = userId;
    }

    public int getUserId()
    {
	return userId;
    }

    public void assigneeOfIssue(int issueId)
    {
	assigneeOfIssues.add("#" + issueId);
    }

    public void secondaryAssigneeOf(int issueId)
    {
	secondaryAssigneeOfIssues.add("#" + issueId);
    }

    public int getTotalAssignedIssues()
    {
	return assigneeOfIssues.size();
    }

    public int getTotalSecondarilyAssignedIssues()
    {
	return secondaryAssigneeOfIssues.size();
    }

    public void printAssignedIssues(String concatBy)
    {
	if (assigneeOfIssues.isEmpty())
	{
	    System.out.print("-");
	}
	else
	{
	    for (String issue : assigneeOfIssues)
	    {
		System.out.print(concatBy + issue);
	    }
	}
    }

    public void printSecondarilyAssignedIssues(String concatBy)
    {
	if (secondaryAssigneeOfIssues.isEmpty())
	{
	    System.out.print("-");
	}
	else
	{
	    for (String issue : secondaryAssigneeOfIssues)
	    {
		System.out.print(concatBy + issue);
	    }
	}
    }
}
