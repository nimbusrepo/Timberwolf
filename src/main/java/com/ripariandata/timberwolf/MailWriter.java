package com.ripariandata.timberwolf;

/**
 * MailWriter is an object that can take some MailboxItems and write them into
 * some repository.
 */
public interface MailWriter
{
    /** Writes the given MailboxItems into the repository. */
    void write(Iterable<MailboxItem> mails);
}