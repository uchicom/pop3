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
    /**
     * アドレスとメールユーザーフォルダの格納フォルダを指定する
     * 
     * @param args
     */
    public static void main(String[] args) {
        Parameter param = new Parameter(args);
        if (param.init(System.err)) {
            execute(param);
        }
    }
  
    
    public PoolServer(String hostName, File file, Socket socket) {
        super(hostName, file);
        this.socket = socket;
    }

    /** メイン処理
     * 
     */
    private static void execute(Parameter param) {

        ExecutorService exec = null;
        ServerSocket server = null;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(param.getPort()), param.getBack());
            serverQueue.add(server);

            exec = Executors.newFixedThreadPool(param.getPool());
            while (true) {
                // サーバーのあくせぷと実施(サーバは一個だからいいけど。
                Socket socket = server.accept();
                if (Pop3Static.DEBUG) System.out.println(format.format(new Date()) + ":"
                        + String.valueOf(socket.getRemoteSocketAddress()));
                //ここの動きが微妙に違う
                exec.execute(new PoolServer(param.getHostName(), param.getBase(), socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (server != null) {
            try {
                server.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            server = null;
        }
        if (exec != null) {
            exec.shutdownNow();
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
