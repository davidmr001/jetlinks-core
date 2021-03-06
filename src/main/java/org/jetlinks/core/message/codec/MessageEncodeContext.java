package org.jetlinks.core.message.codec;

import org.jetlinks.core.message.Message;

/**
 * @author zhouhao
 * @since 1.0.0
 * @see ToDeviceMessageContext
 */
public interface MessageEncodeContext extends MessageCodecContext {

    Message getMessage();

}
