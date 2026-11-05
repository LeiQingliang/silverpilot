// Optional browser smoke: all API responses are synthetic; no live model or backend is called.
const path = require('node:path');
const os = require('node:os');
const { existsSync } = require('node:fs');
function loadPlaywright() {
  try { return require('playwright'); } catch {
    // npm exec exposes its temporary package through PATH, not this script's module search path.
    const locations = (process.env.PATH || '').split(path.delimiter)
      .filter(entry => path.basename(entry) === '.bin').map(entry => path.dirname(entry));
    try { return require(require.resolve('playwright', { paths: locations })); } catch {
      throw new Error('Playwright is optional. Run via npm exec --package=playwright -- node scripts/test/operations-ui.smoke.cjs, or set NODE_PATH to an existing package directory.');
    }
  }
}
const { chromium } = loadPlaywright();
const fs = require('node:fs/promises');
const assert = require('node:assert/strict');
const runId = '0123456789abcdef0123456789abcdef';
const feedbackId = 'abcdef0123456789abcdef0123456789';
const baseUrl = process.env.SILVERPILOT_UI_BASE_URL || 'http://127.0.0.1:5177';
const root = path.resolve(process.env.SILVERPILOT_UI_OUTPUT_DIR || path.join(os.tmpdir(), 'silverpilot-operations-ui-smoke'));
const browserCandidates = [process.env.SILVERPILOT_UI_BROWSER_PATH,
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe'].filter(Boolean);
const browserPath = browserCandidates.find(existsSync);
(async () => {
  await fs.mkdir(root, {recursive:true});
  const browser = await chromium.launch({ ...(browserPath ? { executablePath: browserPath } : {}), headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript(() => {
    sessionStorage.setItem('token', 'ui-fixture-only'); sessionStorage.setItem('roleId', '1');
    sessionStorage.setItem('id', '1'); sessionStorage.setItem('user', JSON.stringify({id:1,roleId:1,name:'演示管理员'}));
  });
  let feedback = []; let emptySample = true;
  const transitions = [];
  await page.route('**/api/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    let data = { code: 200, data: [] };
    if (path === '/api/chat/admin/overview') data = {
      generatedAt:'2026-11-06T12:00:00', analytics:{days:7,totalRuns:5,successRate:40,degradedRuns:1,waitingConfirmation:1,completedRuns:1,failedRuns:1,averageLatencyMs:300,toolCalls:4,p50LatencyMs:300,p95LatencyMs:500,providerMetrics:[{provider:'longcat',runs:5,successRate:40,p50LatencyMs:300,p95LatencyMs:500,totalTokens:100}],recentRuns:[{runId,userId:7,provider:'longcat',inputModality:'TEXT',status:'COMPLETED',createdAt:'2026-11-06T12:00:00',latencyMs:100}]},
      providers:[{id:'longcat',name:'LongCat',model:'LongCat-2.5',configured:false,text:true,vision:false}],
      knowledge:{approvedDocuments:7,ready:true,sources:['fixture']},workBuddy:{configured:false,tools:[]}
    };
    else if (path === '/api/chat/admin/operations/feedback' && request.method() === 'POST') {
      const input = request.postDataJSON(); assert.equal(input.runId, runId);
      feedback = [{...input,id:feedbackId,status:'OPEN',ownerId:1,version:0,createdAt:'2026-11-06T12:00:00'}]; data=feedback[0];
    } else if (path.endsWith('/transition')) {
      const input=request.postDataJSON(); transitions.push(input);
      feedback[0]={...feedback[0],...input,version:feedback[0].version+1}; data=feedback[0];
    } else if (path === '/api/chat/admin/operations/export') {
      data={schemaVersion:'silverpilot-operations-v1',totalRuns:5,runSuccessRate:40,feedbackSummary:{total:1,verified:1}};
    } else if (path === '/api/chat/admin/operations') {
      const verified=feedback.filter(x=>x.status==='VERIFIED').length;
      data={feedback:{items:feedback,truncated:false},feedbackSummary:{total:feedback.length,verified,verifiedClosureRate:feedback.length?100*verified/feedback.length:null},actionOutcomes:{total:3,statuses:{SUCCEEDED:1,CANCELLED:1,EXPIRED:1}}};
    }
    if (path === '/api/chat/admin/overview' && emptySample) data.analytics = {days:7,totalRuns:0,successRate:0,averageLatencyMs:0,p50LatencyMs:0,p95LatencyMs:0,recentRuns:[]};
    await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(data)});
  });
  try {
    await page.goto(new URL('/AgentOperationsView', baseUrl).href, {waitUntil:'networkidle'});
    await page.getByText('问题处理与办理结果',{exact:true}).waitFor();
    assert.equal(await page.locator('.metric-grid article').nth(1).locator('strong').textContent(), '暂无数据');
    assert.equal(await page.locator('.metric-grid article').nth(2).locator('strong').textContent(), '—');
    await page.getByText(/响应耗时 P50：—；P95：—/).waitFor();
    emptySample = false;
    await page.getByRole('button',{name:'刷新数据',exact:true}).click();
    await page.locator('.metric-grid article').nth(1).getByText('40.0%',{exact:true}).waitFor();
    await page.getByRole('combobox',{name:'关联运行',exact:true}).click();
    await page.getByRole('option').filter({hasText:runId}).click();
    await page.getByRole('button',{name:'登记问题',exact:true}).click();
    await page.getByRole('button',{name:'处理',exact:true}).click();
    await page.getByRole('button',{name:'保存处理结果'}).click();
    await page.locator('.workflow-table').getByText('处理中',{exact:true}).waitFor();
    await page.getByRole('button',{name:'处理',exact:true}).click();
    await page.getByPlaceholder('commit:提交哈希 或 issue:编号').fill('commit:abcdef0');
    await page.getByRole('button',{name:'保存处理结果'}).click();
    await page.locator('.workflow-table').getByText('待复测',{exact:true}).waitFor();
    await page.getByRole('button',{name:'处理',exact:true}).click();
    assert.equal(await page.getByRole('button',{name:'保存处理结果'}).isDisabled(),true);
    await page.getByPlaceholder('eval:case-001').fill('eval:case-001');
    await page.getByText('我已检查复测记录，确认问题修复且复测通过', {exact:true}).click();
    await page.getByRole('button',{name:'保存处理结果'}).click();
    await page.locator('.workflow-table').getByText('已验证关闭',{exact:true}).waitFor();
    assert.deepEqual(transitions.map(x=>x.status),['TRIAGED','FIXED','VERIFIED']);
    assert.equal(transitions[2].verificationPassed,true);
    const pending=page.waitForEvent('download');
    await page.getByRole('button',{name:'导出匿名运营报告'}).click();
    const download=await pending;
    const report=await fs.readFile(await download.path(),'utf8');
    assert(!report.includes(runId));
    await page.screenshot({path:root+'/operations-synthetic-desktop.png',fullPage:true});
    await page.setViewportSize({width:390,height:844});
    if (await page.getByRole('button',{name:'关闭菜单',exact:true}).count()) await page.getByRole('button',{name:'关闭菜单',exact:true}).click(); await page.waitForFunction(() => document.querySelector('.admin-sidebar').getBoundingClientRect().right <= 0); await page.screenshot({path:root+'/operations-synthetic-mobile.png',fullPage:true});
    const dimensions=await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth}));
    assert(dimensions.scroll <= dimensions.width,JSON.stringify(dimensions));
    assert.deepEqual(errors,[]);
    console.log(JSON.stringify({passed:true,mode:'browser UI with synthetic API fixtures, no live provider/backend',emptySample:true,outputDirectory:root,transitions:transitions.map(x=>x.status),download:download.suggestedFilename(),mobile:dimensions}));
  } finally { await browser.close(); }
})().catch(error=>{console.error(error);process.exitCode=1});
