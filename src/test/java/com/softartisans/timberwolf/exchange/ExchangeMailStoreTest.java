package com.softartisans.timberwolf.exchange;

import com.cloudera.alfredo.client.AuthenticationException;
import com.microsoft.schemas.exchange.services.x2006.messages.ArrayOfResponseMessagesType;
import com.microsoft.schemas.exchange.services.x2006.messages.FindFolderResponseMessageType;
import com.microsoft.schemas.exchange.services.x2006.messages.FindFolderResponseType;
import com.microsoft.schemas.exchange.services.x2006.messages.ResponseCodeType;
import com.microsoft.schemas.exchange.services.x2006.types.ArrayOfFoldersType;
import com.microsoft.schemas.exchange.services.x2006.types.DistinguishedFolderIdNameType;
import com.microsoft.schemas.exchange.services.x2006.types.FindFolderParentType;
import com.microsoft.schemas.exchange.services.x2006.types.FolderIdType;
import com.microsoft.schemas.exchange.services.x2006.types.FolderType;
import com.microsoft.schemas.exchange.services.x2006.types.MessageType;
import com.softartisans.timberwolf.MailboxItem;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.apache.xmlbeans.XmlException;
import org.junit.Test;

import static com.softartisans.timberwolf.exchange.IsXmlBeansRequest.likeThis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Test for ExchangeMailStore, uses mock exchange service. */
public class ExchangeMailStoreTest extends ExchangeTestBase
{
    private final String idHeaderKey = "Item ID";

    @Test
    public void testGetMailFind0()
            throws XmlException, IOException, HttpErrorException,
                   ServiceCallException, AuthenticationException
    {
        // Exchange returns 0 mail when findItem is called
        MessageType[] messages = new MessageType[0];
        mockFindItem(messages);
        defaultMockFindFolders();
        for (MailboxItem mailboxItem : new ExchangeMailStore(getService()).getMail())
        {
            fail("There shouldn't be any mailBoxItems");
        }
    }

    @Test
    public void testGetMailFind0Folders()
            throws XmlException, IOException, HttpErrorException,
                   ServiceCallException, AuthenticationException
    {
        // Exchange returns 0 mail when findItem is called
        mockFindFolders(new FolderType[0]);
        for (MailboxItem mailboxItem : new ExchangeMailStore(getService()).getMail())
        {
            fail("There shouldn't be any mailBoxItems");
        }
    }

    @Test
    public void testGetMailGet0()
            throws XmlException, IOException, HttpErrorException,
                   ServiceCallException, AuthenticationException
    {
        // Exchange returns 0 mail even though you asked for some mail
        final int count = 100;
        MessageType[] messages = new MessageType[count];
        for (int i = 0; i < count; i++)
        {
            messages[i] = mockMessageItemId("the" + i + "id");
        }
        mockFindItem(messages);
        defaultMockFindFolders();

        try
        {
            Iterable<MailboxItem> mail = new ExchangeMailStore(getService()).getMail();
        }
        catch (ExchangeRuntimeException e)
        {
            assertEquals("Failed to get item details.", e.getMessage());
        }
    }

    @Test
    public void testGetMail30()
            throws XmlException, IOException, HttpErrorException,
                   ServiceCallException, AuthenticationException
    {
        // Exchange returns 30 in FindItems and 30 in GetItems
        final int count = 30;
        MessageType[] findItems = new MessageType[count];
        List<String> requestedList = new Vector<String>(count);
        MessageType[] messages = new MessageType[count];
        for (int i = 0; i < count; i++)
        {
            String id = "the #" + i + " id";
            findItems[i] = mockMessageItemId(id);
            requestedList.add(id);
            messages[i] = mockMessageItemId(id);
        }
        mockFindItem(findItems);
        defaultMockFindFolders();
        mockGetItem(messages, requestedList);
        int i = 0;
        for (MailboxItem mailboxItem : new ExchangeMailStore(getService()).getMail())
        {
            assertEquals(requestedList.get(i), mailboxItem.getHeader(idHeaderKey));
            i++;
        }
        if (i < requestedList.size())
        {
            fail("There were less items returned than there should have been");
        }
    }

    @Test
    public void testFindMailOneIdPageTwoItemPages()
            throws IOException, AuthenticationException, ServiceCallException, HttpErrorException, XmlException
    {
        final int itemsInExchange = 10;
        final int idPageSize = 11;
        final int itemPageSize = 5;
        Configuration config = new Configuration(idPageSize, itemPageSize);
        defaultMockFindFolders();
        MessageType[] findResults = mockFindItem(getDefaultFolderId(), 0, idPageSize, itemsInExchange);
        mockGetItem(findResults, 0, itemPageSize, 0, itemsInExchange, getDefaultFolderId());
        mockGetItem(findResults, 0, itemPageSize, 1, itemsInExchange, getDefaultFolderId());

        FindItemIterator mailItor = new FindItemIterator(getService(), config, getDefaultFolder());

        int index = 0;
        List<String> ids = generateIds(0, itemsInExchange, getDefaultFolderId());
        while (mailItor.hasNext())
        {
            MailboxItem item = mailItor.next();
            assertEquals(ids.get(index), item.getHeader("Item ID"));
            index++;
        }
        assertEquals(itemsInExchange, index);
    }

    @Test
    public void testFindMailOneIdPageFiveItemPages()
            throws IOException, AuthenticationException, ServiceCallException, HttpErrorException, XmlException
    {
        final int itemsInExchange = 24;
        final int idPageSize = 30;
        final int itemPageSize = 5;
        Configuration config = new Configuration(idPageSize, itemPageSize);
        defaultMockFindFolders();
        MessageType[] findResults = mockFindItem(getDefaultFolderId(), 0, idPageSize, itemsInExchange);

        final int mockCount = 5;
        for (int i = 0; i < mockCount; i++)
        {
            mockGetItem(findResults, 0, itemPageSize, i, itemsInExchange, getDefaultFolderId());
        }

        FindItemIterator mailItor = new FindItemIterator(getService(), config, getDefaultFolder());

        int index = 0;
        List<String> ids = generateIds(0, itemsInExchange, getDefaultFolderId());
        while (mailItor.hasNext())
        {
            MailboxItem item = mailItor.next();
            assertEquals(ids.get(index), item.getHeader("Item ID"));
            index++;
        }
        assertEquals(itemsInExchange, index);
    }

    @Test
    public void testFindMailTwoIdPages10ItemPages()
            throws IOException, AuthenticationException, ServiceCallException, HttpErrorException, XmlException
    {
        final int itemsInExchange = 50;
        final int idPageSize = 30;
        final int itemPageSize = 5;
        Configuration config = new Configuration(idPageSize, itemPageSize);
        defaultMockFindFolders();
        // FindItem #1
        MessageType[] findResults = mockFindItem(getDefaultFolderId(), 0, idPageSize, idPageSize);
        final int mockCount = 6;
        for (int i = 0; i < mockCount; i++)
        {
            mockGetItem(findResults, 0, itemPageSize, i, itemsInExchange, getDefaultFolderId());
        }

        // FindItem #2
        findResults = mockFindItem(getDefaultFolderId(), idPageSize, idPageSize, itemsInExchange - idPageSize);
        final int mockCount2 = 4;
        for (int i = 0; i < mockCount2; i++)
        {
            mockGetItem(findResults, idPageSize, itemPageSize, i, itemsInExchange, getDefaultFolderId());
        }

        FindItemIterator mailItor = new FindItemIterator(getService(), config, getDefaultFolder());

        int index = 0;
        List<String> ids = generateIds(0, itemsInExchange, getDefaultFolderId());
        while (mailItor.hasNext())
        {
            MailboxItem item = mailItor.next();
            assertEquals(ids.get(index), item.getHeader("Item ID"));
            index++;
        }
        assertEquals(itemsInExchange, index);
    }

    @Test
    public void testFindMailFiveIdPages20ItemPages()
            throws IOException, AuthenticationException, ServiceCallException, HttpErrorException, XmlException
    {
        final int itemsInExchange = 100;
        final int idPageSize = 20;
        final int itemPageSize = 5;
        Configuration config = new Configuration(idPageSize, itemPageSize);
        defaultMockFindFolders();
        final int mockFindItemCount = 5;
        final int mockGetItemCount = 4;
        for (int i = 0; i < mockFindItemCount; i++)
        {
            MessageType[] findResults = mockFindItem(getDefaultFolderId(), i * idPageSize, idPageSize, idPageSize);
            for (int j = 0; j < mockGetItemCount; j++)
            {
                mockGetItem(findResults, idPageSize * i, itemPageSize, j, itemsInExchange, getDefaultFolderId());
            }
        }
        // because the idPageSize evenly divides the number of emails
        mockFindItem(getDefaultFolderId(), itemsInExchange, idPageSize, 0);

        FindItemIterator mailItor = new FindItemIterator(getService(), config, getDefaultFolder());

        int index = 0;
        List<String> ids = generateIds(0, itemsInExchange, getDefaultFolderId());
        while (mailItor.hasNext())
        {
            MailboxItem item = mailItor.next();
            assertEquals(ids.get(index), item.getHeader("Item ID"));
            index++;
        }
        assertEquals(itemsInExchange, index);
    }

    @Test
    public void testFindMailItemPageLargerThanIdPage()
            throws IOException, AuthenticationException, ServiceCallException, HttpErrorException, XmlException
    {
        final int itemsInExchange = 20;
        final int idPageSize = 5;
        final int itemPageSize = 10;
        Configuration config = new Configuration(idPageSize, itemPageSize);
        defaultMockFindFolders();
        final int mockFindItemCount = 4;
        for (int i = 0; i < mockFindItemCount; i++)
        {
            MessageType[] findResults = mockFindItem(getDefaultFolderId(), i * idPageSize, idPageSize, idPageSize);
            mockGetItem(findResults, idPageSize * i, idPageSize, 0, itemsInExchange, getDefaultFolderId());
        }
        // because the idPageSize evenly divides the number of emails
        mockFindItem(getDefaultFolderId(), itemsInExchange, idPageSize, 0);

        FindItemIterator mailItor = new FindItemIterator(getService(), config, getDefaultFolder());

        int index = 0;
        List<String> ids = generateIds(0, itemsInExchange, getDefaultFolderId());
        while (mailItor.hasNext())
        {
            MailboxItem item = mailItor.next();
            assertEquals(ids.get(index), item.getHeader("Item ID"));
            index++;
        }
        assertEquals(itemsInExchange, index);
    }

    @Test
    public void testGetMailWithPagingAndFolders() throws ServiceCallException, HttpErrorException, XmlException,
            IOException
    {
        FindFolderResponseType folderResponse = mock(FindFolderResponseType.class);
        ArrayOfResponseMessagesType folderArr = mock(ArrayOfResponseMessagesType.class);
        FindFolderResponseMessageType folderMsgs = mock(FindFolderResponseMessageType.class);
        when(folderMsgs.getResponseCode()).thenReturn(ResponseCodeType.NO_ERROR);
        FindFolderParentType parent = mock(FindFolderParentType.class);
        when(parent.isSetFolders()).thenReturn(true);
        ArrayOfFoldersType folders = mock(ArrayOfFoldersType.class);

        FolderType folderOne = mock(FolderType.class);
        FolderIdType folderOneId = mock(FolderIdType.class);
        when(folderOne.isSetFolderId()).thenReturn(true);
        when(folderOneId.getId()).thenReturn("FOLDER-ONE-ID");
        when(folderOne.getFolderId()).thenReturn(folderOneId);

        FolderType folderTwo = mock(FolderType.class);
        FolderIdType folderTwoId = mock(FolderIdType.class);
        when(folderTwo.isSetFolderId()).thenReturn(true);
        when(folderTwoId.getId()).thenReturn("FOLDER-TWO-ID");
        when(folderTwo.getFolderId()).thenReturn(folderTwoId);

        FolderType folderThree = mock(FolderType.class);
        FolderIdType folderThreeId = mock(FolderIdType.class);
        when(folderThree.isSetFolderId()).thenReturn(true);
        when(folderThreeId.getId()).thenReturn("FOLDER-THREE-ID");
        when(folderThree.getFolderId()).thenReturn(folderThreeId);

        when(folders.getFolderArray()).thenReturn(new FolderType[] {folderOne, folderTwo, folderThree});
        when(parent.getFolders()).thenReturn(folders);
        when(folderMsgs.getRootFolder()).thenReturn(parent);
        when(folderMsgs.isSetRootFolder()).thenReturn(true);
        FindFolderResponseMessageType[] fFRMT = new FindFolderResponseMessageType[] {folderMsgs};
        when(folderArr.getFindFolderResponseMessageArray()).thenReturn(fFRMT);
        when(folderResponse.getResponseMessages()).thenReturn(folderArr);

        FindFolderResponseType emptyFolderResponse = mock(FindFolderResponseType.class);
        ArrayOfResponseMessagesType emptyResponseArr = mock(ArrayOfResponseMessagesType.class);
        when(emptyResponseArr.getFindFolderResponseMessageArray()).thenReturn(new FindFolderResponseMessageType[] {});
        when(emptyFolderResponse.getResponseMessages()).thenReturn(emptyResponseArr);

        when(getService().findFolder(likeThis(
                FindFolderHelper.getFindFoldersRequest(DistinguishedFolderIdNameType.MSGFOLDERROOT))))
            .thenReturn(folderResponse);

        final int defaultMaxIds = 10;
        final int countTwo = 2;
        final int countThree = 3;
        final int countFive = 5;
        final int countTen = 10;
        final int offsetZero = 0;
        final int offsetFive = 5;
        final int offsetTen = 10;
        mockFindItem("FOLDER-ONE-ID", offsetZero, defaultMaxIds, countTwo);
        mockGetItem(new MessageType[]{mockMessageItemId("FOLDER-ONE-ID:the #0 id"),
                mockMessageItemId("FOLDER-ONE-ID:the #1 id")},
                generateIds(offsetZero, countTwo, "FOLDER-ONE-ID"));
        mockFindItem("FOLDER-TWO-ID", offsetZero, defaultMaxIds, countTen);
        mockGetItem(new MessageType[] {mockMessageItemId("FOLDER-TWO-ID:the #0 id"),
                                        mockMessageItemId("FOLDER-TWO-ID:the #1 id"),
                                        mockMessageItemId("FOLDER-TWO-ID:the #2 id"),
                                        mockMessageItemId("FOLDER-TWO-ID:the #3 id"),
                                        mockMessageItemId("FOLDER-TWO-ID:the #4 id") },
                    generateIds(offsetZero, countFive, "FOLDER-TWO-ID"));
        mockGetItem(new MessageType[] {mockMessageItemId("FOLDER-TWO-ID:the #5 id"),
                                        mockMessageItemId("FOLDER-TWO-ID:the #6 id"),
                                        mockMessageItemId("FOLDER-TWO-ID:the #7 id"),
                                        mockMessageItemId("FOLDER-TWO-ID:the #8 id"),
                                        mockMessageItemId("FOLDER-TWO-ID:the #9 id"), },
                    generateIds(offsetFive, countFive, "FOLDER-TWO-ID"));
        mockFindItem("FOLDER-TWO-ID", offsetTen, defaultMaxIds, countThree);
        mockGetItem(new MessageType[] {mockMessageItemId("FOLDER-TWO-ID:the #10 id"),
                                        mockMessageItemId("FOLDER-TWO-ID:the #11 id"),
                                        mockMessageItemId("FOLDER-TWO-ID:the #12 id"), },
                    generateIds(offsetTen, countThree, "FOLDER-TWO-ID"));
        mockFindItem("FOLDER-THREE-ID", offsetZero, defaultMaxIds, countTwo);
        mockGetItem(new MessageType[] {mockMessageItemId("FOLDER-THREE-ID:the #0 id"),
                                        mockMessageItemId("FOLDER-THREE-ID:the #1 id") },
                    generateIds(offsetZero, countTwo, "FOLDER-THREE-ID"));

        final int defaultFindItemPageSize = 10;
        final int defaultGetItemPageSize = 5;
        ExchangeMailStore store = new ExchangeMailStore(getService(), defaultFindItemPageSize, defaultGetItemPageSize);
        Iterator<MailboxItem> mail = store.getMail().iterator();
        final int folderId1 = 13;
        final int folderId2 = 2;
        for (String folder : new String[] {"FOLDER-ONE-ID", "FOLDER-TWO-ID", "FOLDER-THREE-ID" })
        {
            for (int i = 0; i < (folder == "FOLDER-TWO-ID" ? folderId1 : folderId2); i++)
            {
                assertTrue(mail.hasNext());
                MailboxItem item = mail.next();
                assertEquals(folder + ":the #" + i + " id", item.getHeader("Item ID"));
            }
        }
        assertFalse(mail.hasNext());
    }
}
