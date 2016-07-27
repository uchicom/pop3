/**
 * (c) 2013 uchicom
 */
package com.uchicom.dirpop3;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Channelを利用したPOP3サーバー
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class SelectorPop3Server {

    protected static Queue<ServerSocketChannel> serverQueue = new ConcurrentLinkedQueue<ServerSocketChannel>();

    /**
     * @param args
     */
    public static void main(String[] args) {
        Pop3Parameter parameter = new Pop3Parameter(args);
        if (parameter.init(System.err)) {
        	SelectorPop3Server server = new SelectorPop3Server();
	    	server.execute(parameter);
        }
    }
    private static boolean alive = true;

    /** メイン処理
     *
     */
    private void execute(Pop3Parameter param) {
        ServerSocketChannel server = null;
        try {
            server = ServerSocketChannel.open();
            server.socket().setReuseAddress(true);
            server.socket().bind(new InetSocketAddress(param.getPort()), param.getBack());
            server.configureBlocking(false);
            serverQueue.add(server);

            Selector selector = Selector.open();
            server.register(selector, SelectionKey.OP_ACCEPT , new AcceptHandler(param.getBase(), param.getHostName()));

            while (alive) {
                if (selector.select() > 0) {
                    Set<SelectionKey> keys = selector.selectedKeys();
                    Iterator<SelectionKey> ite = keys.iterator();
                    while(ite.hasNext()) {
                        SelectionKey key = ite.next();
                        ite.remove();
                        try {
                            if (key.isValid()) {
                                ((Handler)key.attachment()).handle(key);
                            } else {
                                key.cancel();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            key.cancel();
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                            key.cancel();
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
	        synchronized (server) {
	            if (server != null) {
	                try {
	                    server.close();
	                } catch (Exception e) {
	                    e.printStackTrace();
	                }
	                server = null;
	            }
	        }
        }
    }


    public static void shutdown(String[] args) {
        if (!serverQueue.isEmpty()) {
            try {
                serverQueue.poll().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
