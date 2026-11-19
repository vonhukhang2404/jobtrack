// ライト / ダーク の切り替え
//   - 選んだテーマは localStorage に保存（ブラウザごと）
//   - 最初の表示は <head> 内の小さなスクリプトで設定済み（ちらつき防止）
(function () {
    function current() {
        return document.documentElement.getAttribute('data-bs-theme') === 'dark' ? 'dark' : 'light';
    }

    function apply(theme) {
        document.documentElement.setAttribute('data-bs-theme', theme);
        try { localStorage.setItem('jt-theme', theme); } catch (e) { /* 保存できなくても表示は切り替える */ }
        document.querySelectorAll('[data-jt-theme-toggle]').forEach(function (btn) {
            btn.setAttribute('aria-pressed', theme === 'dark' ? 'true' : 'false');
        });
    }

    document.addEventListener('click', function (e) {
        var btn = e.target.closest('[data-jt-theme-toggle]');
        if (btn) {
            apply(current() === 'dark' ? 'light' : 'dark');
        }
    });

    // 削除などの確認ダイアログ：<form data-confirm="文言">（文言は画面の言語で出す）
    document.addEventListener('submit', function (e) {
        var form = e.target;
        var text = form.getAttribute && form.getAttribute('data-confirm');
        if (text && !window.confirm(text)) {
            e.preventDefault();
        }
    });

    document.addEventListener('DOMContentLoaded', function () {
        apply(current());
    });
})();
