import type { ShellResources } from './en';

// Vietnamese copy (NFR-14). Kept type-checked against `en` so keys cannot
// drift, but NOT registered in the i18next config yet: English-only until vi
// is scheduled to ship (registration point: web/src/lib/i18n.ts).
const vi: ShellResources = {
  nav: { dashboard: 'Bảng điều khiển', resumes: 'CV', settings: 'Cài đặt tài khoản' },
  common: { loading: 'Đang tải…', retry: 'Thử lại' },
  mfe: { failed: 'Không tải được phần này.' },
  login: {
    title: 'Đăng nhập',
    subtitle: 'Chọn phương thức đăng nhập để tiếp tục.',
    continueWith: 'Tiếp tục với {{provider}}',
    unavailable: 'Hiện chưa thể đăng nhập.',
    failed: 'Đăng nhập chưa hoàn tất. Vui lòng thử lại.',
    sessionEnded: 'Bạn đã bị đăng xuất. Vui lòng đăng nhập lại để tiếp tục.',
  },
  authError: {
    title: 'Đăng nhập chưa hoàn tất. Vui lòng thử lại.',
    support: 'Liên kết này đã hết hạn hoặc đã được dùng. Hãy liên hệ hỗ trợ.',
    back: 'Quay lại đăng nhập',
  },
  recovery: {
    title: 'Khôi phục quyền truy cập',
    body: 'Liên kết một phương thức đăng nhập để lấy lại quyền truy cập tài khoản. Hệ thống không bao giờ đặt mật khẩu.',
  },
  welcome: {
    title: 'Chọn tên người dùng',
    body: 'Tên người dùng là địa chỉ công khai của bạn. Bước chọn tên sẽ ra mắt cùng tính năng hồ sơ công khai; trước mắt bạn có thể vào bảng điều khiển.',
    cta: 'Tiếp tục đến bảng điều khiển',
  },
  notFound: { title: 'Không tìm thấy trang', body: 'Đường dẫn này không tồn tại hoặc đã thay đổi.', cta: 'Về bảng điều khiển' },
  settings: { promptAdd: 'Thêm email liên hệ để nhận hóa đơn.' },
};

export default vi;
