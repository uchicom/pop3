/**
 * (c) 2013 uchicom
 */
package com.uchicom.dirpop3;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class AcceptHandler implements Handler {

    File base;
    String hostName;
    public AcceptHandler(File base, String hostName) {
        this.base = base;
        this.hostName = hostName;
    }
    /* (non-Javadoc)
     * @see com.uchicom.http.Handler#handle(java.nio.channels.SelectionKey)
     */
    @Override
    public void handle(SelectionKey key) throws IOException {
        if (key.isAcceptable()) {
            //サーバーの受付処理。
            SocketChannel socketChannel = ((ServerSocketChannel)key.channel()).accept();
            socketChannel.configureBlocking(false);
            socketChannel.register(key.selector(), SelectionKey.OP_WRITE, new Pop3Handler(base, hostName));

        }
    }

}
