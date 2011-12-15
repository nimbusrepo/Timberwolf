package com.softartisans.timberwolf;

import com.cloudera.alfredo.client.AuthenticatedURL;
import com.cloudera.alfredo.client.AuthenticationException;
import com.microsoft.schemas.exchange.services.x2006.messages.ArrayOfResponseMessagesType;
import com.microsoft.schemas.exchange.services.x2006.messages.FindItemResponseMessageType;
import com.microsoft.schemas.exchange.services.x2006.messages.FindItemType;
import com.microsoft.schemas.exchange.services.x2006.messages.GetItemType;
import com.microsoft.schemas.exchange.services.x2006.messages.ItemInfoResponseMessageType;
import com.microsoft.schemas.exchange.services.x2006.types.DefaultShapeNamesType;
import com.microsoft.schemas.exchange.services.x2006.types.DistinguishedFolderIdNameType;
import com.microsoft.schemas.exchange.services.x2006.types.DistinguishedFolderIdType;
import com.microsoft.schemas.exchange.services.x2006.types.ItemIdType;
import com.microsoft.schemas.exchange.services.x2006.types.ItemQueryTraversalType;
import com.microsoft.schemas.exchange.services.x2006.types.ItemType;
import com.microsoft.schemas.exchange.services.x2006.types.MessageType;
import com.microsoft.schemas.exchange.services.x2006.types.NonEmptyArrayOfBaseItemIdsType;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlsoap.schemas.soap.envelope.EnvelopeDocument;
import org.xmlsoap.schemas.soap.envelope.EnvelopeType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.Vector;

/**
 * This is the MailStore implementation for Exchange email
 */
public class ExchangeService implements MailStore
{
    private static final Logger log = LoggerFactory.getLogger(
            ExchangeService.class);
    /**
     * When FindItems is run, you can limit the number of items to get at a time
     * and page, starting with 1000, but we'll probably want to profile this a
     * bit to figure out if we want more or less
     */
    private static final int MaxFindItemEntries = 1000;

    /**
     * This is the side of the search results to start paging at.
     * I'm not sure which one is the earliest or latest yet, but the options
     * are "beginning" or "end"
     * TODO change this to an actual enum from our xml binding
     */
    private static final String FindItemsBasePoint = "Beginning";

    /**
     * GetItems takes multiple ids, but we don't want to call GetItems on all
     * MaxFindItemEntries at a time, because those could be massive responses
     * Instead, get a smaller number at a time.
     * This should evenly divide MaxFindItemEntries
     * TODO make this larger
     */
    private static final int MaxGetItemsEntries = 5;

    /**
     * The url of the service, passed in as a command line parameter, or from a
     * config
     */
    private String exchangeUrl;

    public ExchangeService(String exchangeUrl)
            throws IOException, UnsupportedEncodingException,
                   AuthenticationException
    {
        this.exchangeUrl = exchangeUrl;
    }

    private static byte[] getFindItemsRequest(
            int offset, DistinguishedFolderIdNameType.Enum folder)
            throws UnsupportedEncodingException
    {
        EnvelopeDocument envelopeDocument =
                EnvelopeDocument.Factory.newInstance();
        EnvelopeType envelope = envelopeDocument.addNewEnvelope();
        FindItemType findItem = envelope.addNewBody().addNewFindItem();
        findItem.setTraversal(ItemQueryTraversalType.SHALLOW);
        findItem.addNewItemShape().setBaseShape(DefaultShapeNamesType.ID_ONLY);
        DistinguishedFolderIdType folderId =
                findItem.addNewParentFolderIds().addNewDistinguishedFolderId();
        folderId.setId(folder);
        // TODO paging

        String request =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + envelopeDocument
                        .xmlText();
        log.debug(request);
        return request.getBytes("UTF-8");
    }

    private static byte[] getGetItemsRequest(Vector<String> ids)
            throws UnsupportedEncodingException
    {
        EnvelopeDocument envelopeDocument =
                EnvelopeDocument.Factory.newInstance();
        EnvelopeType envelope = envelopeDocument.addNewEnvelope();
        GetItemType getItem = envelope.addNewBody().addNewGetItem();
        getItem.addNewItemShape()
               .setBaseShape(DefaultShapeNamesType.ALL_PROPERTIES);
        NonEmptyArrayOfBaseItemIdsType items = getItem.addNewItemIds();
        ItemIdType itemId = items.addNewItemId();
        itemId.setId(ids.get(0));
        // TODO can we set more than one here??
        String request =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + envelopeDocument
                        .xmlText();
        log.debug(request);
        return request.getBytes("UTF-8");
    }

    @Override
    public Iterable<MailboxItem> getMail(String user)
            throws IOException, AuthenticationException
    {
        return new Iterable<MailboxItem>()
        {
            @Override
            public Iterator<MailboxItem> iterator()
            {
                return new EmailIterator(exchangeUrl);
            }
        };
    }

    private static class EmailIterator implements Iterator<MailboxItem>
    {
        Vector<String> currentIds;
        int currentIdIndex = 0;
        int findItemsOffset = 0;
        String exchangeUrl;
        private Vector<MailboxItem> mailBoxItems;
        private int currentMailboxItemIndex = 0;

        private EmailIterator(String exchangeUrl)
        {
            this.exchangeUrl = exchangeUrl;
        }

        private static HttpURLConnection makeRequest(String exchangeUrl,
                                                     byte[] request)
                throws IOException, AuthenticationException
        {
            AuthenticatedURL.Token token = new AuthenticatedURL.Token();
            URL url = new URL(exchangeUrl);
            HttpURLConnection conn = new AuthenticatedURL().openConnection(url,
                                                                           token);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Content-Type", "text/xml");
            conn.setRequestProperty("Content-Length", "" + request.length);
            conn.getOutputStream().write(request);
            return conn;
        }

        private static Vector<String> findItems(int offset, String exchangeUrl)
                throws IOException, AuthenticationException, XmlException
        {
            byte[] bytes = getFindItemsRequest(
                    offset, DistinguishedFolderIdNameType.INBOX);
            HttpURLConnection conn = makeRequest(exchangeUrl, bytes);

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK)
            {
                EnvelopeDocument doc =
                        EnvelopeDocument.Factory.parse(conn.getInputStream());
                ArrayOfResponseMessagesType array =
                        doc.getEnvelope().getBody().getFindItemResponse()
                           .getResponseMessages();
                Vector<String> items = new Vector<String>();
                for (FindItemResponseMessageType message : array
                        .getFindItemResponseMessageArray())
                {
                    log.debug(message.getResponseCode().toString());
                    for (MessageType item : message.getRootFolder().getItems()
                                                   .getMessageArray())
                    {
                        items.add(item.getItemId().getId());
                    }
                }
                return items;
            }
            else
            {
                log.error("Failed to find items; Status code: "
                          + conn.getResponseCode() + " "
                          + conn.getResponseMessage());
            }
            return new Vector<String>();
        }

        /**
         * Get a list of items from the server
         * @param count the number of items to get.
         * if startIndex+count > ids.size() then only ids.size()-startIndex
         * items will be returned
         * @param startIndex the index in ids of the first item to get
         * @param ids a list of the ids to get
         * @return
         */
        private static Vector<MailboxItem> getItems(int count, int startIndex,
                                                    Vector<String> ids,
                                                    String exchangeUrl)
                throws IOException, AuthenticationException, XmlException
        {
            byte[] bytes = getGetItemsRequest(ids);
            HttpURLConnection conn = makeRequest(exchangeUrl, bytes);

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK)
            {
                EnvelopeDocument doc =
                        EnvelopeDocument.Factory.parse(conn.getInputStream());
                ItemInfoResponseMessageType[] array =
                        doc.getEnvelope().getBody().getGetItemResponse()
                           .getResponseMessages()
                           .getGetItemResponseMessageArray();
                Vector<MailboxItem> items = new Vector<MailboxItem>();
                for (ItemInfoResponseMessageType message : array)
                {
                    for (MessageType item : message.getItems()
                                                   .getMessageArray())
                    {
                        items.add(new ExchangeEmail(item));
                    }

                }
                return items;
            }
            else
            {
                log.error("Failed to find items; Status code: "
                          + conn.getResponseCode() + " "
                          + conn.getResponseMessage());
            }

            return new Vector<MailboxItem>();
        }


        @Override
        public boolean hasNext()
        {
            if (currentIds == null)
            {
                try
                {
                    currentMailboxItemIndex = 0;
                    currentIdIndex = 0;
                    currentIds = findItems(findItemsOffset, exchangeUrl);
                    log.debug("Got " + currentIds.size() + " email ids");
                }
                catch (IOException e)
                {
                    log.error("findItems failed to get ids", e);
                    return false;
                }
                catch (AuthenticationException e)
                {
                    log.error("findItems could not authenticate", e);
                    return false;
                }
                catch (XmlException e)
                {
                    log.error("findItems could not decode response", e);
                    return false;
                }
            }
            // TODO paging here
            if (currentIdIndex >= currentIds.size())
            {
                return false;
            }
            if (mailBoxItems == null)
            {
                try
                {
                    currentMailboxItemIndex = 0;
                    mailBoxItems = getItems(1, currentIdIndex, currentIds, exchangeUrl);
                    log.debug("Got " + mailBoxItems.size() + " emails");
                    return currentMailboxItemIndex < mailBoxItems.size();
                }
                catch (IOException e)
                {
                    log.error("getItems failed", e);
                }
                catch (AuthenticationException e)
                {
                    log.error("getItems could not authenticate", e);
                }
                catch (XmlException e)
                {
                    log.error("getItems could not decode response", e);
                }
            }
            // TODO call getItems more than once
            return currentMailboxItemIndex < mailBoxItems.size();
        }

        @Override
        public MailboxItem next()
        {
            if (currentMailboxItemIndex < mailBoxItems.size())
            {
                MailboxItem item = mailBoxItems.get(currentMailboxItemIndex);
                currentMailboxItemIndex++;
                return item;
            }
            else
            {
                log.debug("All done, " + currentMailboxItemIndex + " >= " + mailBoxItems.size());
                return null;
            }
        }

        @Override
        public void remove()
        {
        }
    }

}
