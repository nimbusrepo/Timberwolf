/**
 * Copyright 2012 Riparian Data
 * http://www.ripariandata.com
 * contact@ripariandata.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ripariandata.timberwolf.writer.hbase;

import com.ripariandata.timberwolf.MockHTable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Assert;
import org.junit.Test;

/** This test confirms that MockHTable works correctly. */
public class MockHTableTest
{
    private static final String TEST_COLUMN_FAMILY_STRING = "s";
    private static final byte[] SYNC_COLUMN_FAMILY = Bytes.toBytes(TEST_COLUMN_FAMILY_STRING);
    private static final byte[] SYNC_COLUMN_QUALIFIER = Bytes.toBytes("v");
    private static final String TEST_TABLE_NAME = "mockTable";

    public static void put(final MockHTable table,
                           final byte[] key,
                           final String value) throws IOException
    {
        List<Put> puts = new ArrayList<Put>();
        Put put = new Put(key);
        put.add(SYNC_COLUMN_FAMILY, SYNC_COLUMN_QUALIFIER,
                Bytes.toBytes(value));

        puts.add(put);
        table.put(puts);
    }

    public static String get(final MockHTable table,
                             final byte[] key) throws IOException
    {
        Get get = new Get(key);
        Result result = null;
        result = table.get(get);
        Assert.assertEquals(1, result.size());
        return Bytes.toString(result.getValue(SYNC_COLUMN_FAMILY,
                                              SYNC_COLUMN_QUALIFIER));
    }

    @Test
    public void testResetValue()
    {
        final String value1 = "val1";
        final String value2 = "val2";
        final byte[] key = Bytes.toBytes("key");

        MockHTable table = MockHTable.create(TEST_TABLE_NAME);

        try
        {
            put(table, key, value1);
            Assert.assertEquals(value1, get(table, key));
            put(table, key, value2);
            Assert.assertEquals(value2, get(table, key));
        }
        catch (IOException e)
        {
            throw new HBaseRuntimeException("Could not get from HBase!", e);
        }
    }
}
