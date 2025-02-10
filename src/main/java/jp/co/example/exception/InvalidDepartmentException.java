package jp.co.example.exception;

public class InvalidDepartmentException extends RuntimeException {
    
	//デフォルトコンストラクタ
	public InvalidDepartmentException() {
		super();
	}
	
	//メッセージを受け取るコンストラクタ
	public InvalidDepartmentException(String message) {
        super(message);
    }
	
	//メッセージと原因を受け取るコンストラクタ
	public InvalidDepartmentException(String message, Throwable cause) {
		super(message, cause);
	}
}
