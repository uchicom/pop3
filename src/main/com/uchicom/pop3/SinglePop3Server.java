/**
 * (c) 2012 uchicom
 */
package com.uchicom.pop3;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * POP3サーバー
 * シングルスレッド.
 * @author Uchiyama Shigeki
 * 
 */
public class SinglePop3Server {

	/** ファイルとUIDLで使用する日時フォーマット */
	public static final SimpleDateFormat format = new SimpleDateFormat(
			Pop3Static.DATE_TIME_MILI_FORMAT);
	
	/** 実行モード(デフォルト：シングルスレッドモード) */
	public static int EXEC_MODE = 0;
	
	/** シングルスレッドモード */
	public static final int EXEC_SINGLE_THREAD = 0;
	
	/** マルチスレッドモード */
	public static final int EXEC_MULTI_THREAD = 1;
	
	/** チャンネルモード */
	public static final int EXEC_CHANNEL = 2;

	protected static Queue<ServerSocket> serverQueue = new ConcurrentLinkedQueue<ServerSocket>();
	protected String hostName;
	protected  File file;
	protected int port;
	protected int back;
	protected FileComparator comparator = new FileComparator();
	/**
	 * アドレスとメールユーザーフォルダの格納フォルダを指定する
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length != 2) {
			System.out.println("args.length != 2");
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
        if (args.length == 3) {
            back = Integer.parseInt(args[3]);
        }
        
		execute(hostName, file, port, back);
		

	}

	/**
	 * pop3のみ実施するコンストラクタ.
	 * @param hostName
	 * @param file
	 */
    public SinglePop3Server(String hostName, File file) {
        this.hostName = hostName;
        this.file = file;
    }
	/** メイン処理
	 * 
	 */
	private static void execute(String hostName, File file, int port, int back) {
	    ServerSocket server = null;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(port), back);
            serverQueue.add(server);
            
            SinglePop3Server smtpServer = new SinglePop3Server(hostName, file);
            while (true) {
                Socket socket = server.accept();
                System.out.println(format.format(new Date()) + ":"
                        + String.valueOf(socket.getRemoteSocketAddress()));
                smtpServer.pop3(socket);
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
                    } finally {
                        server = null;
                    }
                }
            }
        }
	}
	
	/**
	 * pop3処理.
	 * @param socket
	 */
	public void pop3(Socket socket) {

        System.out.println(format.format(new Date()) + ":"
                + String.valueOf(socket.getRemoteSocketAddress()));
        // 0.はプロセスごとに変える番号だけど、とくに複数プロセスを持っていないので。
        String timestamp = "<" +Thread.currentThread().getId() + "." + System.currentTimeMillis() + "@" + hostName
                + ">";
        BufferedReader br = null;
        PrintStream ps = null;
        try {
            br = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            ps = new PrintStream(socket.getOutputStream());
            // 1接続に対する受付開始
            ps.print(Pop3Static.RECV_OK);
            ps.print(timestamp);
            ps.print(Pop3Static.RECV_LINE_END);
            ps.flush();
            // 以下はログイン中のみ有効な変数
            String head = br.readLine();
            // ユーザーコマンドでユーザーが設定されたかどうかのフラグ
            boolean bUser = false;
            // 認証が許可されたかどうかのフラグ
            boolean bPass = false;
            String user = null;
            String pass = null;
            File userBox = null;
            // メールbox内にあるメールリスト(PASSコマンド時に認証が許可されると設定される)
            List<File> mailList = null;
            // DELEコマンド時に指定したメールが格納される(PASSコマンド時に認証が許可されると設定される)
            List<File> delList = null;
            while (head != null) {
                // デバッグ文字列
                System.out.println("[" + head + "]");
                if (head.matches(Pop3Static.REG_EXP_USER_NAME)) {
                    bUser = true;
                    user = head.split(" ")[1];
                    ps.print(Pop3Static.RECV_OK_LINE_END);
                    ps.flush();
                } else if (head.matches(Pop3Static.REG_EXP_PASS_WORD)) {
                    if (bUser && !bPass) {
                        pass = head.split(" ")[1];
                        // ユーザーチェック
                        boolean existUser = false;
                        for (File box : file.listFiles()) {
                            if (box.isDirectory()) {
                                if (user.equals(box.getName())) {
                                    userBox = box;
                                    File[] mails = userBox
                                            .listFiles(new FilenameFilter() {

                                                @Override
                                                public boolean accept(
                                                        File dir,
                                                        String name) {
                                                    File file = new File(
                                                            dir, name);
                                                    if (file.isFile()
                                                            && !file.isHidden()
                                                            && file.canRead()
                                                            && !Pop3Static.PASSWORD_FILE_NAME
                                                                    .equals(name)) {
                                                        return true;
                                                    }
                                                    return false;
                                                }

                                            });

                                    mailList = Arrays.asList(mails);
                                    Collections.sort(mailList, comparator);
                                    delList = new ArrayList<File>();
                                    
                                    existUser = true;
                                }
                            }
                        }
                        if (existUser) {
                            // パスワードチェック
                            if (!"".equals(pass)) {
                                File passwordFile = new File(userBox,
                                        Pop3Static.PASSWORD_FILE_NAME);
                                if (passwordFile.exists()
                                        && passwordFile.isFile()) {
                                    BufferedReader passReader = new BufferedReader(
                                            new InputStreamReader(
                                                    new FileInputStream(
                                                            passwordFile)));
                                    String password = passReader
                                            .readLine();
                                    while ("".equals(password)) {
                                        password = passReader
                                                .readLine();
                                    }
                                    passReader.close();
                                    if (pass.equals(password)) {
                                        ps.print(Pop3Static.RECV_OK_LINE_END);
                                        bPass = true;
                                    } else {
                                        // パスワード不一致エラー
                                        ps.print(Pop3Static.RECV_NG_LINE_END);
                                    }
                                } else {
                                    // パスワードファイルなしエラー
                                    ps.print(Pop3Static.RECV_NG_LINE_END);
                                }
                            } else {
                                // パスワード入力なしエラー
                                ps.print(Pop3Static.RECV_NG_LINE_END);
                            }
                        } else {
                            // ユーザー存在しないエラー
                            ps.print(Pop3Static.RECV_NG_LINE_END);
                        }
                    } else {
                        // ユーザー名未入力エラー
                        ps.print(Pop3Static.RECV_NG_LINE_END);
                    }
                    ps.flush();
                } else if (head.matches(Pop3Static.REG_EXP_STAT)) {
                    if (bPass) {
                        // 簡易一覧表示
                        ps.print(Pop3Static.RECV_OK);
                        long fileLength = 0;
                        int fileCnt = 0;
                        for (File child : mailList) {
                            if (!delList.contains(child)) {
                                fileLength += child.length();
                                fileCnt++;
                            }
                        }
                        ps.print(fileCnt);
                        ps.print(" ");
                        ps.print(fileLength);
                        ps.print(Pop3Static.RECV_LINE_END);
                    } else {
                        // 認証なしエラー
                        ps.print(Pop3Static.RECV_NG_LINE_END);
                    }
                    ps.flush();
                } else if (head.matches(Pop3Static.REG_EXP_LIST)) {
                    if (bPass) {
                        // リスト表示
                        ps.print(Pop3Static.RECV_OK_LINE_END);
                        for (int i = 0; i < mailList.size(); i++) {
                            File child = mailList.get(i);
                            if (!delList.contains(child)) {
                                ps.print(i + 1);
                                ps.print(' ');
                                ps.print(child.length());
                                ps.print(Pop3Static.RECV_LINE_END);
                            }
                        }
                        ps.print(Pop3Static.RECV_DATA_END);
                    } else {
                        // 認証なしエラー
                        ps.print(Pop3Static.RECV_NG_LINE_END);
                    }
                    ps.flush();
                } else if (head.matches(Pop3Static.REG_EXP_LIST_NUM)) {
                    if (bPass) {
                        // 指定番号のリスト表示
                        String[] heads = head.split(" ");
                        int index = Integer.parseInt(heads[1]) - 1;
                        if (0 <= index && index < mailList.size()) {
                            File child = mailList.get(index);
                            if (!delList.contains(child)) {
                                ps.print(Pop3Static.RECV_OK);
                                ps.print(head.substring(5));
                                ps.print(' ');
                                ps.print(child.length());
                                ps.print(Pop3Static.RECV_LINE_END);
                            } else {
                                ps.print(Pop3Static.RECV_NG_LINE_END);
                            }
                        } else {
                            // index範囲外
                            ps.print(Pop3Static.RECV_NG_LINE_END);
                        }
                    } else {
                        // 認証なしエラー
                        ps.print(Pop3Static.RECV_NG_LINE_END);
                    }
                    ps.flush();
                } else if (head.matches(Pop3Static.REG_EXP_RETR)) {
                    if (bPass) {
                        ps.print(Pop3Static.RECV_OK_LINE_END);
                        for (File child : mailList) {
                            if (!delList.contains(child)) {
                                BufferedReader passReader = new BufferedReader(
                                        new InputStreamReader(
                                                new FileInputStream(
                                                        child)));
                                String readLine = passReader.readLine();
                                while (readLine != null) {
                                    ps.print(readLine);
                                    ps.print(Pop3Static.RECV_LINE_END);
                                    ps.flush();
                                    readLine = passReader.readLine();
                                }
                                passReader.close();
                            }
                        }
                        ps.print(Pop3Static.RECV_DATA_END);
                    } else {
                        // エラー
                        ps.print(Pop3Static.RECV_NG_LINE_END);
                    }
                    ps.flush();
                } else if (head.matches(Pop3Static.REG_EXP_RETR_NUM)) {
                    if (bPass) {
                        String[] heads = head.split(" ");
                        int index = Integer.parseInt(heads[1]) - 1;
                        if (0 <= index && index < mailList.size()) {
                            File child = mailList.get(index);
                            if (!delList.contains(child)) {
                                ps.print(Pop3Static.RECV_OK);
                                ps.print(child.length());
                                ps.print(Pop3Static.RECV_LINE_END);
                                BufferedReader passReader = new BufferedReader(
                                        new InputStreamReader(
                                                new FileInputStream(
                                                        child)));
                                String readLine = passReader.readLine();
                                while (readLine != null) {
                                    ps.print(readLine);
                                    ps.print(Pop3Static.RECV_LINE_END);
                                    ps.flush();
                                    readLine = passReader.readLine();
                                }
                                ps.print(Pop3Static.RECV_DATA_END);
                                passReader.close();
                            } else {
                                ps.print(Pop3Static.RECV_NG_LINE_END);
                            }
                        } else {
                            // index範囲外
                            ps.print(Pop3Static.RECV_NG_LINE_END);
                        }
                    } else {
                        // エラー
                        ps.print(Pop3Static.RECV_NG_LINE_END);
                    }
                    ps.flush();
                } else if (head.matches(Pop3Static.REG_EXP_DELE_NUM)) {
                    if (bPass) {
                        // 削除処理
                        String[] heads = head.split(" ");
                        int index = Integer.parseInt(heads[1]) - 1;
                        if (0 <= index && index < mailList.size()) {
                            File child = mailList.get(index);
                            delList.add(child);
                            ps.print(Pop3Static.RECV_OK_LINE_END);
                        } else {
                            // index範囲外
                            ps.print(Pop3Static.RECV_NG_LINE_END);
                        }
                    } else {
                        // エラー
                        ps.print(Pop3Static.RECV_NG_LINE_END);
                    }
                    ps.flush();
                } else if (head.matches(Pop3Static.REG_EXP_RSET)) {
                    // リセット
                    if (bPass) {
                        // 消去マークを無くす
                        delList.clear();
                        ps.print(Pop3Static.RECV_OK_LINE_END);
                    } else {
                        // エラー
                        ps.print(Pop3Static.RECV_NG_LINE_END);
                    }
                    ps.flush();
                } else if (head.matches(Pop3Static.REG_EXP_QUIT)) {
                    if (delList != null) {
                        // 消去マークの入ったファイルを削除する
                        for (File delFile : delList) {
                            delFile.delete();
                        }
                    }
                    ps.print(Pop3Static.RECV_OK_LINE_END);
                    ps.flush();
                    // 削除失敗時は-ERRを返すべきだけどまだやってない。
                    break;
                } else if (head.matches(Pop3Static.REG_EXP_NOOP)) {
                    // 何もしない
                } else if (head.matches(Pop3Static.REG_EXP_TOP_NUM_NUM)) {
                    if (bPass) {
                        // TRANSACTION 状態でのみ許可される
                        String[] heads = head.split(" ");
                        int index = Integer.parseInt(heads[1]) - 1;
                        if (0 <= index && index < mailList.size()) {
                            File child = mailList.get(index);
                            if (!delList.contains(child)) {
                                ps.print(Pop3Static.RECV_OK_LINE_END);
                                BufferedReader passReader = new BufferedReader(
                                        new InputStreamReader(
                                                new FileInputStream(
                                                        child)));
                                String readLine = passReader.readLine();
                                int maxRow = Integer.parseInt(heads[2]);
                                int row = 0;
                                while (readLine != null && row < maxRow) {
                                    ps.print(readLine);
                                    ps.print(Pop3Static.RECV_LINE_END);
                                    ps.flush();
                                    readLine = passReader.readLine();
                                    row++;
                                }
                                ps.print(Pop3Static.RECV_DATA_END);
                                passReader.close();
                            } else {
                                ps.print(Pop3Static.RECV_NG_LINE_END);
                            }
                        } else {
                            ps.print(Pop3Static.RECV_NG_LINE_END);
                        }
                    } else {
                        // 認証なしエラー
                        ps.print(Pop3Static.RECV_NG_LINE_END);
                    }
                    ps.flush();
                } else if (head.matches(Pop3Static.REG_EXP_UIDL)) {
                    if (bPass) {
                        // TRANSACTION 状態でのみ許可される
                        ps.print(Pop3Static.RECV_OK_LINE_END);
                        for (int i = 0; i < mailList.size(); i++) {
                            File child = mailList.get(i);
                            if (!delList.contains(child)) {
                                ps.print(i + 1);
                                ps.print(' ');
                                String name = child.getName();
                                int lastIndex = name.lastIndexOf('~');
                                if (lastIndex < 0) {
                                    if (name.length() > 70) {
                                        lastIndex = name.length() - 70;
                                    } else {
                                        ps.print(name);
                                    }
                                } else {
                                    ps.print(name.substring(lastIndex));
                                }
                                ps.print(Pop3Static.RECV_LINE_END);
                            }
                        }
                        ps.print(Pop3Static.RECV_DATA_END);
                    } else {
                        // 認証なしエラー
                        ps.print(Pop3Static.RECV_NG_LINE_END);
                    }
                    ps.flush();
                } else if (head.matches(Pop3Static.REG_EXP_UIDL_NUM)) {
                    if (bPass) {
                        // TRANSACTION 状態でのみ許可される
                        String[] heads = head.split(" ");
                        int index = Integer.parseInt(heads[1]) - 1;
                        if (0 <= index && index < mailList.size()) {
                            File child = mailList.get(index);
                            if (!delList.contains(child)) {
                                ps.print(Pop3Static.RECV_OK);
                                ps.print(heads[1]);
                                ps.print(' ');
                                String name = child.getName();
                                int lastIndex = name.lastIndexOf('~');
                                if (lastIndex < 0) {
                                    if (name.length() > 70) {
                                        lastIndex = name.length() - 70;
                                    } else {
                                        ps.print(name);
                                    }
                                } else {
                                    ps.print(name.substring(lastIndex));
                                }
                                ps.print(Pop3Static.RECV_LINE_END);
                            } else {
                                ps.print(Pop3Static.RECV_NG_LINE_END);
                            }
                        } else {
                            // index範囲外
                            ps.print(Pop3Static.RECV_NG_LINE_END);
                        }
                    } else {
                        // 認証なしエラー
                        ps.print(Pop3Static.RECV_NG_LINE_END);
                    }
                    ps.flush();
                } else if (head
                        .matches(Pop3Static.REG_EXP_APOP_NAME_DIGEST)) {
                    if (!bPass) {
                        // 未実装のためエラーとする
                        String[] heads = head.split(" ");
                        user = heads[1];
                        String digest = heads[2];
                        // ユーザーチェック
                        boolean existUser = false;
                        for (File box : file.listFiles()) {
                            if (box.isDirectory()) {
                                if (user.equals(box.getName())) {
                                    userBox = box;
                                    File[] mails = userBox
                                            .listFiles(new FilenameFilter() {
                                                @Override
                                                public boolean accept(
                                                        File dir,
                                                        String name) {
                                                    File file = new File(
                                                            dir, name);
                                                    if (file.isFile()
                                                            && !file.isHidden()
                                                            && !Pop3Static.PASSWORD_FILE_NAME
                                                                    .equals(name)) {
                                                        return true;
                                                    }
                                                    return false;
                                                }
                                            });
                                    mailList = Arrays.asList(mails);
                                    Collections.sort(mailList, comparator);
                                    delList = new ArrayList<File>();
                                    existUser = true;
                                }
                            }
                        }
                        if (existUser) {
                            // パスワードチェック
                            File passwordFile = new File(userBox,
                                    Pop3Static.PASSWORD_FILE_NAME);
                            if (passwordFile.exists()
                                    && passwordFile.isFile()) {
                                BufferedReader passReader = new BufferedReader(
                                        new InputStreamReader(
                                                new FileInputStream(
                                                        passwordFile)));
                                String password = passReader.readLine();
                                while ("".equals(password)) {
                                    password = passReader.readLine();
                                }
                                passReader.close();
                                // ダイジェストとタイムスタンプを元にダイジェストを作成
                                MessageDigest md = MessageDigest
                                        .getInstance("MD5");
                                md.update((timestamp + password)
                                        .getBytes());
                                byte[] passBytes = md.digest();
                                StringBuffer strBuff = new StringBuffer(
                                        32);
                                for (int i = 0; i < passBytes.length; i++) {
                                    int d = passBytes[i] & 0xFF;
                                    if (d < 0x10) {
                                        strBuff.append("0");
                                    }
                                    strBuff.append(Integer
                                            .toHexString(d));
                                }
                                if (digest.equals(strBuff.toString())) {
                                    ps.print(Pop3Static.RECV_OK_LINE_END);
                                    bPass = true;
                                } else {
                                    // パスワード不一致エラー
                                    ps.print(Pop3Static.RECV_NG_LINE_END);
                                }
                            } else {
                                // パスワードファイルなしエラー
                                ps.print(Pop3Static.RECV_NG_LINE_END);
                            }
                        } else {
                            // ユーザー存在しないエラー
                            ps.print(Pop3Static.RECV_NG_LINE_END);
                        }
                    } else {
                        // パスワード認証後に再度パスワード認証はエラー
                        ps.print(Pop3Static.RECV_NG_LINE_END);
                    }
                    ps.flush();
                } else if ("".equals(head)) {
                    // 何もしない
                } else {
                    ps.print(Pop3Static.RECV_NG_CMD_NOT_FOUND);
                    ps.flush();
                }
                head = br.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            if (ps != null) {
                try {
                    ps.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (br != null) {
                try {
                    br.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            synchronized (socket) {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        socket = null;
                    }
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
