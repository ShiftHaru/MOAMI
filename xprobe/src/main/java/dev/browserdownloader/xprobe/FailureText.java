package dev.browserdownloader.xprobe;

/** Fixed user-facing reasons; remote exception text may contain signed URLs or credentials. */
public final class FailureText {
    private FailureText() { }

    public static String describe(Throwable failure) {
        if (StorageErrors.isNoSpace(failure)) return "저장 공간이 부족합니다. 공간을 확보한 뒤 다시 시도하세요.";
        if (failure instanceof InterruptedException) return "작업을 중지했습니다.";
        if (failure instanceof java.net.SocketTimeoutException) return "네트워크 응답 시간이 초과되었습니다. 연결을 확인한 뒤 재시도하세요.";
        if (failure instanceof java.net.UnknownHostException || failure instanceof java.net.ConnectException)
            return "서버에 연결할 수 없습니다. 네트워크 연결을 확인하세요.";
        if (failure instanceof javax.net.ssl.SSLException) return "서버의 보안 연결을 확인할 수 없습니다.";
        if (failure instanceof SecurityException) return "파일 접근 권한을 확인하세요.";
        String message = failure.getMessage();
        if (message == null) return "작업에 실패했습니다. 연결과 저장 공간을 확인한 뒤 재시도하세요.";
        if (message.matches("MP4 요청 실패: HTTP [1-5][0-9]{2}")) return message + " · 서버 응답으로 저장하지 못했습니다.";
        return switch (message) {
            case "내부 임시 파일 복구 실패" -> "이전 작업의 내부 임시 파일을 정리하지 못했습니다. 앱을 다시 실행해 주세요.";
            case "MP4 구조 검증 실패" -> "MP4 파일 구조가 유효하지 않거나 검사 범위를 초과했습니다.";
            case "MP4 영상 트랙 정보 읽기 실패", "MP4 해상도 정보 없음", "MP4 재생 시간 정보 없음" -> message + " · 기기에서 영상 정보를 확인하지 못했습니다.";
            case "영상 프레임 읽기 실패", "MP4 프레임 디코딩 실패" -> "기기에서 MP4 영상 프레임을 읽지 못했습니다.";
            case "해상도를 확인한 공개 MP4 후보가 없습니다." -> "해상도를 확인할 수 있는 MP4 후보가 없습니다.";
            case "MP4 픽셀 한도 초과" -> "MP4 파일의 해상도가 유효하지 않거나 픽셀 한도를 초과했습니다.";
            case "저장 복구 자체 검사 실패", "시험 기록 실패", "시험 파일 정리 실패" -> "저장 복구 자체 검사에 실패했습니다. 시험 파일이 남을 수 있으므로 저장 상태를 확인해 주세요.";
            case "미완료 저장 복구 실패" -> "이전 미완료 파일을 정리하지 못했습니다. 저장 공간·파일 접근을 확인한 뒤 다시 시도하세요.";
            case "authentication-required" -> "이 게시물은 로그인이 필요합니다. 현재는 지원하지 않습니다.";
            case "rate-limited" -> "X 요청 한도에 도달했습니다. 잠시 후 재시도하세요.";
            case "unavailable" -> "게시물을 찾을 수 없거나 접근할 수 없습니다.";
            case "network-timeout", "검사 제한 시간 초과" -> "게시물 조회 시간이 초과되었습니다. 잠시 후 재시도하세요.";
            case "extraction-failed" -> "게시물 정보를 읽지 못했습니다. 접근 제한 또는 추출기 변경 여부를 확인해야 합니다.";
            case "공개 X 게시물의 https 주소를 입력하세요.", "올바른 X 게시물 주소가 아닙니다." -> "올바른 공개 X 게시물의 HTTPS 주소를 입력하세요.";
            case "현재 GIF 검증 범위는 30초 이하입니다.", "현재 GIF 검증 범위는 1080p 픽셀 수 이하입니다.",
                    "GIF 변환 시간 한도 초과", "GIF 시험 파일 한도 100 MiB 초과",
                    "사진 시험 한도 초과", "MP4 시험 한도 500 MiB 초과", "MP4 저장 시험 시간 초과" -> message;
            case "사진 응답 형식 불일치", "사진 수신 크기 불일치", "사진 디코딩 정보 불일치",
                    "MP4 응답 형식 불일치", "MP4 수신 크기 불일치", "선택 후보와 저장 파일의 해상도 불일치" -> "미디어 형식·크기 검증에 실패하여 정상 파일로 저장하지 않았습니다.";
            case "공용 저장 파일 검증 실패", "다운로드 완료 처리 실패", "다운로드 파일 쓰기 실패",
                    "다운로드 파일 생성 실패" -> "다운로드 폴더 저장에 실패했습니다. 접근 권한과 저장 공간을 확인하세요.";
            case "미지원 미디어 유형" -> "현재 지원하지 않는 미디어 유형입니다.";
            default -> "작업에 실패했습니다. 연결과 저장 공간을 확인한 뒤 재시도하세요.";
        };
    }
}
