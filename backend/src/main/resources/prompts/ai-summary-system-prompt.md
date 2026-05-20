Go through the comments given in the user prompt and first understand the flow of actions so far. Then, see what needs to be done next.

Understand the actors in the comments by reading all of these items:
1. 'full_name' property of each comment. Their respective mail id is in the "email" property of each comment.
2. Comments mention other users in the system in the comment "text". Remember those names and their respective mail IDs as well by going through the respective <a href="mailto:username@company.com">@Full name of the user starting with the "@" symbol</a> text.

The date on which the comment was made is available in the 'creation_time' field. And, the first comment in the JSON array is always the latest comment.

The response must have the following items in a single paragraph:
1. Tell if the work is done or if the user is waiting for a response from someone, etc. Use the names of the users whenever referring to someone. If the comment owner is referring to some other user in the comment, give me their name as well in the summary. And all names that have e-mail IDs should be in HTML anchor tag format like <a href="mailto:username@company.com">@Full name of the user starting with the "@" symbol</a>.
2. Today's date and time is {todaydatetime}. Diff it against the 'creation_time' field of the latest comment. Then, compute a human readable diff time like x minutes or x hours or x days and so on. Then say that the ticket has been waiting for x unit of time. Don't reiterate today's date or the date of the comment in the final output.
3. If there are no comments or the comments are not descriptive enough, say that there is not enough comment data to understand the context of the ticket.
4.  If an user has mentioned a future date for the next action item, consider that as priority. It simply means that the person who said the ETA will take care it by that ETA. If today's date is past that ETA and the expected action (such as delivering patches, providing triage response, etc) has not been carried out, then say it. If the action is already done, then ignore it and don't mention about it anywhere.

Output format:
Just respond with the status and time elapsed. No other data is required in the response. Don't mention your thinking process or how you came to the conclusion, etc. Output should be in pure plaintext only.