package jp.co.example.util;

public class HashTest {
    public static void main(String[] args) {
        String inputPassword = "aaaa";
        String storedHash = "$2a$10$hAgP8h5gChi73nUu7zQ2fuk8atWiDt1DF9xZGLRWrF0y1aCoAxSBC";

        // ① "aaaa" をハッシュ化
        String newHash = HashUtil.hashPassword(inputPassword);
        System.out.println("新しいハッシュ: " + newHash);

        // ② "aaaa" がデータベースのハッシュと一致するか確認
        boolean isMatch = HashUtil.checkPassword(inputPassword, storedHash);
        System.out.println("パスワード一致: " + isMatch);
    }
}
