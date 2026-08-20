<template>
  <footer class="global-footer">
    <div class="footer-wrapper">
      <div class="footer-brand">
        <span aria-hidden="true">S</span>
        <div><strong>SilverPilot</strong><small>社区智慧养老综合服务平台</small></div>
      </div>
      <div class="footer-notes" aria-label="系统边界">
        <span>本地演示</span><span>写操作人工确认</span><span>不替代医疗诊断</span>
      </div>
      <small class="footer-copy">© 2026 智慧养老服务系统</small>
    </div>
  </footer>
</template>

<style scoped>
.global-footer { position: relative; z-index: 1; width: 100%; padding: 30px 24px; color: var(--sp-text-muted); background: #ededf0; border-top: 1px solid rgb(0 0 0 / 8%); }
.footer-wrapper { width: min(var(--sp-content-max), 100%); display: grid; grid-template-columns: 1fr auto auto; align-items: center; gap: 24px; margin: 0 auto; }
.footer-brand { display: flex; align-items: center; gap: 10px; }
.footer-brand > span { width: 36px; height: 36px; display: grid; place-items: center; color: var(--sp-on-brand); font-weight: 700; background: var(--sp-brand); border-radius: 11px; }
.footer-brand div { display: grid; line-height: 1.25; }.footer-brand strong { color: var(--sp-text); font-size: 14px; }.footer-brand small { color: var(--sp-text-muted); font-size: 11px; }
.footer-notes { display: flex; flex-wrap: wrap; justify-content: center; gap: 7px; }
.footer-notes span { padding: 5px 9px; color: var(--sp-text-secondary); font-size: 10px; background: rgb(255 255 255 / 58%); border: 1px solid rgb(0 0 0 / 8%); border-radius: var(--sp-radius-pill); }
.footer-copy { color: var(--sp-text-muted); white-space: nowrap; }
@media (max-width: 820px) { .footer-wrapper { grid-template-columns: 1fr; justify-items: center; text-align: center; }.footer-notes { order: 3; } }
</style>
