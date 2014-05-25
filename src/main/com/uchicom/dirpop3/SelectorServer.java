/**
 * (c) 2013 uchicom
 */
package com.uchicom.dirpop3;

import java.io.File;
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
public class SelectorServer {

    protected static Queue<ServerSocketChannel> serverQueue = new ConcurrentLinkedQueue<ServerSocketChannel>();
    
    /**
     * @param args
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("args.length < 1");
            return;
        }
        // メールフォルダ格納フォルダ
        File base = Pop3Static.DEFAULT_MAILBOX;

        if (args.length > 1) {
            base = new File(args[0]);
        }
        if (!base.exists() || !base.isDirectory()) {
            System.err.println("mailbox directory is not found.");
            return;
        }

        // メール
        String hostName = args[1];

        // ポート
        int port = Pop3Static.DEFAULT_PORT;
        if (args.length > 2) {
            port = Integer.parseInt(args[2]);
        } 
        // 接続待ち数
        int back =  Pop3Static.DEFAULT_BACK;
        if (args.length == 3) {
            back = Integer.parseInt(args[3]);
        }
        
        execute(hostName, base, port, back);

    }
    private static boolean alive = true;
    
    /** メイン処理
     * 
     */
    private static void execute(String hostName, File base, int port, int back) {
        ServerSocketChannel server = null;
        try {
            server = ServerSocketChannel.open();
            server.socket().setReuseAddress(true);
            server.socket().bind(new InetSocketAddress(port), back);
            server.configureBlocking(false);
            serverQueue.add(server);
            
            Selector selector = Selector.open();
            server.register(selector, SelectionKey.OP_ACCEPT , new AcceptHandler(base, hostName));

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
        }
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
