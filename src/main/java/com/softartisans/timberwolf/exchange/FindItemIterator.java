package com.softartisans.timberwolf.exchange;

import com.softartisans.timberwolf.MailboxItem;
import java.util.Iterator;
import java.util.Vector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iterates over find item calls
 */
public class FindItemIterator extends BaseChainIterator<MailboxItem>
{
    private static final Logger LOG = LoggerFactory.getLogger(FindItemIterator.class);
    private ExchangeService service;
    private int currentStart;
    private int pageSize;
    private String folder;
    private int getItemsPageSize;

    public FindItemIterator(ExchangeService exchangeService, String folderId,
                            int idsPageSize, int itemsPageSize)
    {
        service = exchangeService;
        pageSize = idsPageSize;
        folder = folderId;
        currentStart = 0;
        getItemsPageSize = itemsPageSize;
    }

    @Override
    protected Iterator<MailboxItem> createIterator()
    {
        try
        {
            Vector<String> messageIds = FindItemHelper.findItems(service, folder, currentStart, pageSize);
            currentStart += pageSize;
            LOG.debug("Got {} email ids.", messageIds.size());
            if (messageIds.size() > 0)
            {
                return new GetItemIterator(service, messageIds, getItemsPageSize);
            }
            else
            {
                return null;
            }
        }
        catch (ServiceCallException e)
        {
            LOG.error("Failed to find item ids.", e);
            throw new ExchangeRuntimeException("Failed to find item ids.", e);
        }
        catch (HttpErrorException e)
        {
            LOG.error("Failed to find item ids.", e);
            throw new ExchangeRuntimeException("Failed to find item ids.", e);
        }
    }
}
