/**
 * [담당: 송귀성] 동시 실행 개수를 제한하는 작업 줄 + 실패 시 재시도 (2026-10-12)
 * 리포트가 카드 10장을 한꺼번에 그리면 카드마다 실거래 조회를 동시에 보내서, 무료 AI 엔진(메모리 512MB)이 한 번에 너무 많은 국토부 자료를
 * 올리다 메모리 초과로 죽는 일이 있었다. 그래서 요청을 limit개씩만 동시에 보내고 나머지는 줄을 세운다(먼저 그려진 카드부터 차례로 처리).
 *
 * - createTaskQueue(limit): run(task)로 넣은 작업을 동시에 최대 limit개만 실행한다. task는 Promise를 돌려주는 함수다.
 * - runQueuedWithRetry(queue, task, {retries, delayMs}): 실패하면 delayMs 뒤 다시 줄에 서서 retries번까지 다시 시도한다.
 *   기다리는 동안은 자리를 차지하지 않는다(다른 카드가 먼저 처리된다).
 *
 * 브라우저에서는 window.CustomHouseTaskQueue, 노드 테스트(frontend/tests/task-queue.test.js)에서는 require로 쓴다.
 */
(function (root) {
  function createTaskQueue(limit) {
    const max = Math.max(1, Number(limit) || 1);
    let active = 0;
    const waiting = [];

    function pump() {
      while (active < max && waiting.length > 0) {
        const job = waiting.shift();
        active += 1;
        Promise.resolve()
          .then(job.task)
          .then(job.resolve, job.reject)
          .finally(() => {
            active -= 1;
            pump();
          });
      }
    }

    function run(task) {
      return new Promise((resolve, reject) => {
        waiting.push({ task, resolve, reject });
        pump();
      });
    }

    return {
      run,
      get active() {
        return active;
      },
      get waiting() {
        return waiting.length;
      },
    };
  }

  const defaultSleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

  async function runQueuedWithRetry(queue, task, options) {
    const opts = options || {};
    const retries = opts.retries == null ? 1 : opts.retries;
    const delayMs = opts.delayMs == null ? 2000 : opts.delayMs;
    const sleep = opts.sleep || defaultSleep;
    let attempt = 0;
    for (;;) {
      try {
        return await queue.run(task);
      } catch (e) {
        if (attempt >= retries) throw e;
        attempt += 1;
        await sleep(delayMs);
      }
    }
  }

  const api = { createTaskQueue, runQueuedWithRetry };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  if (root) root.CustomHouseTaskQueue = api;
})(typeof window !== "undefined" ? window : null);
