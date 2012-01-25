package com.ripariandata.timberwolf.exchange;

import org.slf4j.Logger;

/** Non-checked exception thrown by ExchangeMailStore iterator. */
public class ExchangeRuntimeException extends RuntimeException
{
    public ExchangeRuntimeException(final String message, final Throwable cause)
    {
        super(message, cause);
    }

    /**
     * Logs a ExchangeRuntimeException to the appropriate logs.
     * @param logger The logger to use for logging.
     * @param e The ExchangeRuntimeException to log.
     * @return The ExchangeRuntimeException logged.
     */
    public static ExchangeRuntimeException log(final Logger logger, final ExchangeRuntimeException e)
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
