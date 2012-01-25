package com.ripariandata.timberwolf.hbase;

import org.slf4j.Logger;

/** Non-checked exception thrown by classes that talk to HBase. */
public class HBaseRuntimeException extends RuntimeException
{
    public HBaseRuntimeException(final String message, final Throwable cause)
    {
        super(message, cause);
    }

    public static HBaseRuntimeException log(final Logger logger, final HBaseRuntimeException e)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug(e.getMessage(), e);
        }
        else
        {
            logger.error(e.getMessage());
        }

        return e;
    }
}

