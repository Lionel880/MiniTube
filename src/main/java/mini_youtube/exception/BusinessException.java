package mini_youtube.exception;

/**
 * 代表「預期內」、訊息本身就是安全可以直接顯示給使用者看的商業邏輯錯誤
 * （例如：帳號或密碼錯誤、找不到影片、檔案格式不支援...）。
 *
 * 與其讓 GlobalExceptionHandler 對所有 RuntimeException（可能包含 NullPointerException
 * 等未預期例外，其訊息可能夾帶內部類別/欄位名稱）都原樣回傳 ex.getMessage()，
 * 我們只對「主動拋出的 BusinessException」回傳訊息內容，其餘未預期例外一律回傳
 * 通用的錯誤訊息，避免資訊洩漏。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
