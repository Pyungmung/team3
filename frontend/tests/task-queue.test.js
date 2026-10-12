// [담당: 송귀성] 동시 실행 제한 작업 줄(src/js/utils/task-queue.js) 단위 테스트 (2026-10-12).
// 실행: node frontend/tests/task-queue.test.js  (프로젝트 루트에서) - 실패하면 종료 코드 1
const assert = require("assert");
const { createTaskQueue, runQueuedWithRetry } = require("../src/js/utils/task-queue.js");

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  // ---- 동시 실행 개수 제한: 10개를 넣어도 한 번에 3개까지만 실행된다 ----
  {
    const q = createTaskQueue(3);
    let running = 0;
    let peak = 0;
    const finished = [];
    const tasks = Array.from({ length: 10 }, (_, i) =>
      q.run(async () => {
        running += 1;
        peak = Math.max(peak, running);
        await sleep(15);
        running -= 1;
        finished.push(i);
        return i;
      })
    );
    assert.strictEqual(q.active, 3, "처음에 3개만 시작");
    assert.strictEqual(q.waiting, 7, "나머지 7개는 줄에서 대기");
    const results = await Promise.all(tasks);
    assert.deepStrictEqual(results, [0, 1, 2, 3, 4, 5, 6, 7, 8, 9], "결과는 넣은 순서대로 돌려준다");
    assert.strictEqual(peak, 3, "동시에 3개를 넘지 않는다");
    assert.strictEqual(q.active, 0);
    assert.strictEqual(q.waiting, 0);
    // 먼저 넣은 작업이 먼저 시작된다 (앞 카드부터 채워짐)
    assert.deepStrictEqual(finished.slice(0, 3).sort((a, b) => a - b), [0, 1, 2]);
  }

  // ---- 한 작업이 실패해도 줄은 계속 돈다 ----
  {
    const q = createTaskQueue(2);
    const bad = q.run(async () => {
      throw new Error("boom");
    });
    const good = q.run(async () => "ok");
    await assert.rejects(bad, /boom/);
    assert.strictEqual(await good, "ok");
    assert.strictEqual(q.active, 0);
  }

  // ---- limit 이 이상한 값이어도 최소 1개는 실행한다 ----
  {
    for (const bad of [0, -3, NaN, undefined]) {
      const q = createTaskQueue(bad);
      assert.strictEqual(await q.run(async () => 7), 7, `limit=${bad}`);
    }
  }

  // ---- 재시도: 한 번 실패 -> 기다린 뒤 다시 줄에 서서 성공 ----
  {
    const q = createTaskQueue(1);
    let calls = 0;
    const waits = [];
    const out = await runQueuedWithRetry(
      q,
      async () => {
        calls += 1;
        if (calls === 1) throw new Error("first fail");
        return "recovered";
      },
      { retries: 1, delayMs: 2000, sleep: async (ms) => waits.push(ms) }
    );
    assert.strictEqual(out, "recovered");
    assert.strictEqual(calls, 2);
    assert.deepStrictEqual(waits, [2000], "재시도 전에 2초 기다린다");
  }

  // ---- 재시도: 계속 실패하면 retries 번 다시 해 본 뒤 마지막 오류를 던진다 ----
  {
    const q = createTaskQueue(1);
    let calls = 0;
    await assert.rejects(
      runQueuedWithRetry(
        q,
        async () => {
          calls += 1;
          throw new Error("always");
        },
        { retries: 1, delayMs: 1, sleep: async () => {} }
      ),
      /always/
    );
    assert.strictEqual(calls, 2, "처음 1번 + 재시도 1번");
  }

  // ---- 재시도: 성공하면 재시도하지 않는다 / retries=0 이면 재시도 없음 ----
  {
    const q = createTaskQueue(1);
    let calls = 0;
    await runQueuedWithRetry(q, async () => ++calls, { retries: 1, sleep: async () => {} });
    assert.strictEqual(calls, 1);
    let calls0 = 0;
    await assert.rejects(
      runQueuedWithRetry(q, async () => { calls0 += 1; throw new Error("x"); }, { retries: 0, sleep: async () => {} })
    );
    assert.strictEqual(calls0, 1);
  }

  // ---- 재시도 대기 중에는 줄 자리를 차지하지 않는다 (다른 작업이 먼저 처리됨) ----
  {
    const q = createTaskQueue(1);
    const order = [];
    let n = 0;
    const retrying = runQueuedWithRetry(
      q,
      async () => {
        n += 1;
        if (n === 1) throw new Error("fail once");
        order.push("retry-ok");
      },
      { retries: 1, delayMs: 30 }
    );
    await sleep(5);
    const other = q.run(async () => order.push("other"));
    await Promise.all([retrying, other]);
    assert.deepStrictEqual(order, ["other", "retry-ok"], "대기하는 동안 다른 카드가 먼저 처리된다");
  }

  console.log("task-queue: 모든 테스트 통과");
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
