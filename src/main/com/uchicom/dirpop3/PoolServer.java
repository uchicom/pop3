/**
 * (c) 2013 uchicom
 */
package com.uchicom.dirpop3;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * マルチスレッドのPOP3サーバー.
 * スレッドプールを使用.
 * @author Uchiyama Shigeki
 *
 */
public class PoolServer extends SingleServer implements Runnable {

    
    protected Socket socket;
    protected int pool;
    /**
     * アドレスとメールユーザーフォルダの格納フォルダを指定する
     * 
     * @param args
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("args.length < 2");
            return;
        }
        // メールフォルダ格納フォルダ
        File file = new File(args[0]);
        if (!file.exists() || !file.isDirectory()) {
            System.out.println("mailbox directory is not found.");
            return;
        }

        // メール
        String hostName = args[1];
        
        // ポート
        int port = 8115;
        if (args.length > 2) {
            port = Integer.parseInt(args[2]);
        } 
        // 接続待ち数
        int back = 10;
        if (args.length > 3) {
            back = Integer.parseInt(args[3]);
        }
        // スレッドプール数
        int pool = 10;
        if (args.length > 4) {
            pool = Integer.parseInt(args[3]);
        }

        execute(hostName, file, port, back, pool);
        

    }
  
    
    public PoolServer(String hostName, File file, Socket socket) {
        super(hostName, file);
        this.socket = socket;
    }

    /** メイン処理
     * 
     */
    private static void execute(String hostName, File file, int port, int back, int pool) {

        ExecutorService exec = null;
        ServerSocket server = null;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(port), back);
            serverQueue.add(server);

            exec = Executors.newFixedThreadPool(pool);
            while (true) {
                // サーバーのあくせぷと実施(サーバは一個だからいいけど。
                Socket socket = server.accept();
                System.out.println(format.format(new Date()) + ":"
                        + String.valueOf(socket.getRemoteSocketAddress()));
                //ここの動きが微妙に違う
                exec.execute(new PoolServer(hostName, file, socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (server != null) {
                try {
                    server.close();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    server = null;
                }
            }
            if (exec != null) {
                exec.shutdownNow();
            }
        }
    }
    
    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {
        pop3(socket);
    }

}
