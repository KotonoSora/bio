import type { AccountResources } from './en';

// Vietnamese copy (NFR-14). Kept type-checked against `en` so keys cannot
// drift, but NOT registered in the i18next config yet: English-only until vi
// is scheduled to ship (registration point: mfe/account/src/lib/i18n.ts).
const vi: AccountResources = {
  common: { cancel: 'Hủy', close: 'Đóng', dismiss: 'Bỏ qua', loading: 'Đang tải…', dash: '—' },
  login: { continueWith: 'Tiếp tục với {{provider}}', failed: 'Đăng nhập chưa hoàn tất. Vui lòng thử lại.' },
  settings: {
    title: 'Cài đặt tài khoản',
    accountEmail: 'Email đăng nhập',
    idpSource: 'Do nhà cung cấp đăng nhập cung cấp',
    status: { verified: 'Đã xác minh', notVerified: 'Chưa xác minh', relay: 'Email ẩn danh', conflict: 'Trùng email' },
    promptAdd: 'Thêm email liên hệ để nhận hóa đơn.',
    conflictNotice: 'Email này đã được kết nối với một tài khoản khác. Hãy đặt email liên hệ thay thế.',
    contactEmail: 'Email liên hệ',
    contactPlaceholder: 'ban@example.com',
    sendCode: 'Gửi mã xác minh',
    codeLabel: 'Mã gồm 6 chữ số',
    verifyCode: 'Xác minh mã',
    resend: 'Gửi lại mã',
    resendIn: 'Gửi lại sau {{seconds}} giây',
    codeSentTo: 'Đã gửi mã 6 chữ số đến {{email}}.',
    attemptsLeft: 'Số lần thử còn lại: {{count}}',
    providers: 'Phương thức đăng nhập',
    linked: 'Đã liên kết',
    notLinked: 'Chưa liên kết',
    link: 'Liên kết',
    unlink: 'Bỏ liên kết',
    lastLinkHint: 'Bạn cần ít nhất một phương thức đăng nhập. Hãy liên kết phương thức khác trước.',
    unlinkTitle: 'Bỏ liên kết {{provider}}?',
    unlinkBody:
      'Phương thức đăng nhập này sẽ bị gỡ khỏi tài khoản và có thể liên kết lại bất cứ lúc nào. Khi xác nhận, bạn có thể được yêu cầu xác minh danh tính.',
    sessions: 'Phiên đang hoạt động',
    current: 'Thiết bị này',
    revoke: 'Thu hồi',
    revokeTitle: 'Thu hồi phiên này?',
    revokeBody: 'Phiên có thể mất tối đa 60 giây để ngừng hoạt động.',
    revokeAll: 'Đăng xuất mọi thiết bị khác',
    revokeAllTitle: 'Đăng xuất mọi thiết bị khác?',
    revokeAllBody:
      'Các phiên khác có thể mất tối đa 60 giây để ngừng hoạt động. Thiết bị này vẫn duy trì đăng nhập.',
    signOut: 'Đăng xuất',
    network: 'Mạng',
    unknownDevice: 'Thiết bị không xác định',
  },
  buckets: { now: 'Vừa hoạt động', today: 'Hôm nay', week: 'Tuần này', earlier: 'Trước đó' },
  stepUp: {
    title: 'Xác nhận là bạn',
    actionLine: 'Bạn sắp thực hiện: {{action}}',
    window: 'Xác nhận có hiệu lực trong 10 phút.',
  },
  actions: { link: 'Liên kết {{provider}}', unlink: 'Bỏ liên kết {{provider}}' },
  errors: {
    throttled: 'Quá nhiều lần thử. Vui lòng thử lại sau {{seconds}} giây.',
    stepUpRequired: 'Hãy xác nhận là bạn để tiếp tục.',
    emailConflict: 'Không thể dùng email này. Hãy chọn email khác.',
    invalidCode: 'Mã không đúng. Hãy kiểm tra hoặc yêu cầu mã mới.',
    lastLink: 'Bạn cần ít nhất một phương thức đăng nhập. Hãy liên kết phương thức khác trước.',
    sessionGone: 'Phiên đó không còn tồn tại.',
    sessionEnded: 'Bạn đã bị đăng xuất. Vui lòng đăng nhập lại để tiếp tục.',
    unavailable: 'Tạm thời không khả dụng. Vui lòng thử lại.',
    generic: 'Đã xảy ra lỗi. Vui lòng thử lại.',
  },
};

export default vi;
