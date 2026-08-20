<template>
  <div id="account-layout" class="account-shell">
    <FrontHeaderMenu />
    <div class="account-frame">
      <aside class="account-sidebar">
        <div class="account-label">
          <span>个人服务</span>
          <strong>我的服务中心</strong>
        </div>
        <MyAsideMenu />
      </aside>
      <section class="account-content" aria-label="个人服务内容">
        <RouterView />
      </section>
    </div>
  </div>
</template>

<script setup>
import FrontHeaderMenu from '../home/FrontHeaderMenu.vue'
import MyAsideMenu from '../home/MyAsideMenu.vue'
</script>

<style scoped>
.account-shell { min-height: 100vh; padding-top: var(--sp-header-height); background: transparent; }
.account-frame {
  width: min(var(--sp-content-max), calc(100% - 40px));
  display: grid;
  grid-template-columns: 248px minmax(0, 1fr);
  gap: 24px;
  margin: 0 auto;
  padding: 30px 0 48px;
}
.account-sidebar {
  position: sticky;
  top: calc(var(--sp-header-height) + 20px);
  align-self: start;
  overflow: hidden;
  color: var(--sp-text);
  background: var(--sp-surface);
  border: 1px solid #e5e5ea;
  border-radius: var(--sp-radius-lg);
  box-shadow: var(--sp-shadow-sm);
}
.account-label { display: grid; gap: 5px; padding: 22px 19px 16px; border-bottom: 1px solid #e5e5ea; }
.account-label span { color: var(--sp-text-muted); font-size: 10px; font-weight: 600; letter-spacing: .04em; }
.account-label strong { color: var(--sp-text); font-size: 18px; font-weight: 700; letter-spacing: -.02em; }
.account-content { min-width: 0; }
.account-content :deep(> *) { min-height: 100%; }

@media (max-width: 900px) {
  .account-frame { width: min(100% - 24px, 760px); grid-template-columns: 1fr; padding-top: 16px; }
  .account-sidebar { position: static; overflow-x: auto; }
  .account-label { display: none; }
}
</style>
