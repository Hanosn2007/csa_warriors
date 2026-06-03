const state = {
  data: null,
  selectedUnitId: null,
  pathDebug: null,
  strategies: [],
  autoTimer: null,
  batchTimers: {}
};

const teamKeys = ["redA", "redB", "blueA", "blueB", "greenA", "greenB", "yellowA", "yellowB"];
const teamLabels = {
  redA: "红队 A 策略",
  redB: "红队 B 策略",
  blueA: "蓝队 A 策略",
  blueB: "蓝队 B 策略",
  greenA: "绿队 A 策略",
  greenB: "绿队 B 策略",
  yellowA: "黄队 A 策略",
  yellowB: "黄队 B 策略"
};
const defaultConfig = {
  redA: "TeamHanson2Warrior",
  redB: "TeamHanson2Warrior",
  blueA: "AllianceEncirclingWarrior",
  blueB: "AllianceEncirclingWarrior",
  greenA: "AllianceEncirclingWarrior",
  greenB: "AllianceEncirclingWarrior",
  yellowA: "AllianceEncirclingWarrior",
  yellowB: "AllianceEncirclingWarrior"
};

function el(id) {
  return document.getElementById(id);
}

async function api(path, body) {
  const init = body ? {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  } : undefined;
  const response = await fetch(path, init);
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text);
  }
  return response.json();
}

async function refreshState() {
  state.data = await api("/api/state");
  render();
}

function render() {
  if (!state.data) return;
  el("roundText").textContent = `当前回合: ${state.data.round} / ${state.data.roundLimit}`;
  el("resultText").textContent = state.data.result;
  el("intentText").textContent = state.data.lastDecisionTrace || state.data.lastIntent || "比赛尚未开始。";
  el("logText").textContent = state.data.logs.length ? state.data.logs.join("\n") : "比赛尚未开始行动。";
  renderBoard();
  renderScores();
  renderUnits();
  renderSelected();
  renderPathDebugStatus();
}

function renderBoard() {
  const data = state.data;
  const board = el("board");
  board.innerHTML = "";
  board.style.gridTemplateColumns = `repeat(${data.map.cols}, 1fr)`;
  board.style.gridTemplateRows = `repeat(${data.map.rows}, 1fr)`;

  const unitsByPos = new Map();
  for (const team of data.teams) {
    for (const unit of team.units) {
      if (unit.alive) unitsByPos.set(`${unit.row},${unit.col}`, { team, unit });
    }
  }
  const powerByPos = new Map(data.powerUps.map(p => [`${p.row},${p.col}`, p]));

  for (let row = 0; row < data.map.rows; row++) {
    for (let col = 0; col < data.map.cols; col++) {
      const cell = document.createElement("button");
      const tile = data.map.tiles[row][col];
      cell.className = "cell";
      if (tile === "WALL") cell.classList.add("wall");
      if (tile === "HEALING_POINT") cell.classList.add("healing");

      const heat = heatValueAt(row, col);
      if (heat) {
        const heatNode = document.createElement("span");
        heatNode.className = "heatCell";
        heatNode.style.background = heat.color;
        heatNode.title = `cost: ${heat.cost}`;
        cell.appendChild(heatNode);

        const heatLabel = document.createElement("span");
        heatLabel.className = "heatLabel";
        heatLabel.textContent = heat.label;
        cell.appendChild(heatLabel);
      }

      const power = powerByPos.get(`${row},${col}`);
      if (power) {
        const node = document.createElement("span");
        node.className = `power ${powerClass(power.type)}`;
        node.textContent = power.shortName;
        cell.appendChild(node);
      }

      const found = unitsByPos.get(`${row},${col}`);
      if (found) {
        const hp = document.createElement("span");
        hp.className = "hp";
        hp.innerHTML = `<i style="width:${Math.max(0, found.unit.health / found.unit.maxHealth * 100)}%"></i>`;
        cell.appendChild(hp);

        const unit = document.createElement("span");
        unit.className = `unit team-${found.team.symbol}`;
        if (found.unit.id === state.selectedUnitId) unit.classList.add("selected");
        if (found.unit.id === data.lastActingUnitId) unit.classList.add("last");
        if (shouldSlideUnit(found.unit, data.animation, data.lastActingUnitId)) {
          cell.classList.add("movingCell");
          unit.classList.add("sliding");
          unit.style.setProperty("--slide-x", `${(data.animation.from.col - data.animation.to.col) * 142.857}%`);
          unit.style.setProperty("--slide-y", `${(data.animation.from.row - data.animation.to.row) * 142.857}%`);
        }
        unit.textContent = found.team.symbol;
        cell.appendChild(unit);

        cell.addEventListener("mouseenter", event => showUnitTooltip(found.unit, event));
        cell.addEventListener("mousemove", moveUnitTooltip);
        cell.addEventListener("mouseleave", hideUnitTooltip);
      }

      cell.addEventListener("click", async () => {
        await handleBoardClick(found, row, col);
      });
      board.appendChild(cell);
    }
  }

  renderDebugPathLayer(board, state.pathDebug, data.map.rows, data.map.cols);
  renderAnimationLayer(board, data.animation, data.map.rows, data.map.cols);
}

function powerClass(type) {
  if (type === "HEALTH" || type === "MEGA_HEALTH") return "health";
  if (type === "ATTACK" || type === "POWER_CORE") return "attack";
  if (type === "RANGE") return "range";
  return "core";
}

async function handleBoardClick(found, row, col) {
  if (found) {
    const selected = findSelectedUnit();
    const shouldSelectUnit = !selected || found.unit.id === selected.id || found.unit.teamName === selected.teamName;
    if (!shouldSelectUnit && state.selectedUnitId) {
      await loadPathDebug({ row, col });
      render();
      return;
    }
    state.selectedUnitId = found.unit.id;
    await loadPathDebug(null);
    render();
    return;
  }

  if (state.selectedUnitId) {
    await loadPathDebug({ row, col });
    render();
    return;
  }

  state.selectedUnitId = found ? found.unit.id : null;
  state.pathDebug = null;
  render();
}

async function loadPathDebug(target) {
  if (!state.selectedUnitId) {
    state.pathDebug = null;
    return;
  }
  const body = { unitId: state.selectedUnitId };
  if (target) {
    body.row = target.row;
    body.col = target.col;
  }
  state.pathDebug = await api("/api/debug/path", body);
}

function clearPathDebug() {
  state.pathDebug = null;
  render();
}

function heatValueAt(row, col) {
  const debug = state.pathDebug;
  if (!debug || !debug.available || !debug.costMap || !debug.costMap[row]) return null;
  const cost = debug.costMap[row][col];
  if (cost == null || cost < 0) return null;
  const span = Math.max(1, debug.maxCost - debug.minCost);
  const rate = Math.max(0, Math.min(1, (cost - debug.minCost) / span));
  const alpha = 0.16 + rate * 0.56;
  return {
    cost,
    label: String(cost),
    color: `rgba(222, 73, 63, ${alpha.toFixed(3)})`
  };
}

function shouldSlideUnit(unit, animation, lastActingUnitId) {
  return animation
    && animation.type === "MOVE"
    && unit.id === lastActingUnitId
    && animation.from
    && animation.to;
}

function showUnitTooltip(unit, event) {
  const tooltip = el("unitTooltip");
  const healthRate = unit.maxHealth > 0 ? Math.max(0, unit.health / unit.maxHealth * 100) : 0;
  tooltip.innerHTML = `
    <strong>#${unit.id} ${unit.name}</strong>
    <span>${unit.teamName}</span>
    <dl>
      <dt>生命</dt><dd>${unit.health}/${unit.maxHealth} (${healthRate.toFixed(0)}%)</dd>
      <dt>攻击</dt><dd>${unit.attackPower} (+${unit.attackBonus})</dd>
      <dt>范围</dt><dd>${unit.range} (+${unit.rangeBonus})</dd>
      <dt>位置</dt><dd>(${unit.row}, ${unit.col})</dd>
      <dt>状态</dt><dd>${unit.alive ? (unit.defending ? "defending" : "alive") : "defeated"}</dd>
    </dl>`;
  tooltip.hidden = false;
  moveUnitTooltip(event);
}

function moveUnitTooltip(event) {
  const tooltip = el("unitTooltip");
  if (!tooltip || tooltip.hidden) return;
  const margin = 12;
  const rect = tooltip.getBoundingClientRect();
  let left = event.clientX + margin;
  let top = event.clientY + margin;
  if (left + rect.width > window.innerWidth) left = event.clientX - rect.width - margin;
  if (top + rect.height > window.innerHeight) top = event.clientY - rect.height - margin;
  tooltip.style.left = `${Math.max(8, left)}px`;
  tooltip.style.top = `${Math.max(8, top)}px`;
}

function hideUnitTooltip() {
  const tooltip = el("unitTooltip");
  if (tooltip) tooltip.hidden = true;
}

function renderDebugPathLayer(board, debug, rows, cols) {
  if (!debug || !debug.available || !debug.path || debug.path.length === 0) return;

  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("class", "debugPathLayer");
  svg.setAttribute("viewBox", `0 0 ${cols} ${rows}`);
  svg.setAttribute("preserveAspectRatio", "none");

  if (debug.path.length > 1) {
    const polyline = document.createElementNS("http://www.w3.org/2000/svg", "polyline");
    polyline.setAttribute("points", debug.path.map(p => `${p.col + 0.5},${p.row + 0.5}`).join(" "));
    polyline.setAttribute("stroke-width", Math.max(0.07, Math.min(rows, cols) * 0.007));
    polyline.setAttribute("stroke-linejoin", "round");
    polyline.setAttribute("stroke-linecap", "round");
    svg.appendChild(polyline);
  }

  if (debug.target) {
    const target = document.createElementNS("http://www.w3.org/2000/svg", "circle");
    target.setAttribute("class", "debugTarget");
    target.setAttribute("cx", debug.target.col + 0.5);
    target.setAttribute("cy", debug.target.row + 0.5);
    target.setAttribute("r", 0.32);
    target.setAttribute("stroke-width", 0.08);
    svg.appendChild(target);
  }

  board.appendChild(svg);
}

function renderAnimationLayer(board, animation, rows, cols) {
  if (!animation || animation.type === "STAY") return;

  const fromX = animation.from.col + 0.5;
  const fromY = animation.from.row + 0.5;
  const toX = animation.to.col + 0.5;
  const toY = animation.to.row + 0.5;
  const strokeWidth = Math.max(0.08, Math.min(rows, cols) * 0.008);
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("class", `animationLayer animation-${animation.type.toLowerCase()}`);
  svg.setAttribute("viewBox", `0 0 ${cols} ${rows}`);
  svg.setAttribute("preserveAspectRatio", "none");

  if (animation.type === "ATTACK") {
    appendSvgLine(svg, fromX, fromY, toX, toY, strokeWidth * 1.35);
  } else if (animation.type === "MOVE") {
    appendSvgLine(svg, fromX, fromY, toX, toY, strokeWidth);
    appendSvgCircle(svg, toX, toY, 0.18, true);
  } else if (animation.type === "POWER") {
    appendSvgPulse(svg, fromX, fromY, 0.35, 0.95);
  } else if (animation.type === "DEFEND") {
    appendSvgPulse(svg, fromX, fromY, 0.45, 0.75);
  }

  board.appendChild(svg);
}

function appendSvgLine(svg, x1, y1, x2, y2, strokeWidth) {
  const line = document.createElementNS("http://www.w3.org/2000/svg", "line");
  line.setAttribute("x1", x1);
  line.setAttribute("y1", y1);
  line.setAttribute("x2", x2);
  line.setAttribute("y2", y2);
  line.setAttribute("stroke-width", strokeWidth);
  line.setAttribute("stroke-linecap", "round");
  svg.appendChild(line);
}

function appendSvgCircle(svg, cx, cy, radius, filled) {
  const circle = document.createElementNS("http://www.w3.org/2000/svg", "circle");
  circle.setAttribute("cx", cx);
  circle.setAttribute("cy", cy);
  circle.setAttribute("r", radius);
  circle.setAttribute("stroke-width", 0.07);
  if (filled) circle.setAttribute("class", "filled");
  svg.appendChild(circle);
}

function appendSvgPulse(svg, cx, cy, fromRadius, toRadius) {
  const circle = document.createElementNS("http://www.w3.org/2000/svg", "circle");
  circle.setAttribute("cx", cx);
  circle.setAttribute("cy", cy);
  circle.setAttribute("r", fromRadius);
  circle.setAttribute("stroke-width", 0.08);

  const grow = document.createElementNS("http://www.w3.org/2000/svg", "animate");
  grow.setAttribute("attributeName", "r");
  grow.setAttribute("from", fromRadius);
  grow.setAttribute("to", toRadius);
  grow.setAttribute("dur", "520ms");
  grow.setAttribute("fill", "freeze");
  circle.appendChild(grow);

  const fade = document.createElementNS("http://www.w3.org/2000/svg", "animate");
  fade.setAttribute("attributeName", "opacity");
  fade.setAttribute("from", "0.82");
  fade.setAttribute("to", "0");
  fade.setAttribute("dur", "520ms");
  fade.setAttribute("fill", "freeze");
  circle.appendChild(fade);

  svg.appendChild(circle);
}

function renderScores() {
  renderTable(el("scoreTable"), ["队伍", "总分", "伤害", "击败", "道具", "回血", "扣分", "无效", "无进展"],
    state.data.scores.map(s => [
      s.teamName, s.totalScore, s.damageDealt, s.defeats, s.powerUps,
      s.healingDone, s.penaltyScore, s.invalidActions, s.noProgressPenalties
    ]));
}

function renderUnits() {
  const rows = [];
  for (const team of state.data.teams) {
    for (const unit of team.units) {
      rows.push([
        team.name,
        `#${unit.id}`,
        unit.name,
        `${unit.health}/${unit.maxHealth}`,
        unit.attackPower,
        unit.range,
        `(${unit.row}, ${unit.col})`,
        unit.alive ? (unit.defending ? "defending" : "alive") : "defeated"
      ]);
    }
  }
  renderTable(el("unitTable"), ["队伍", "ID", "名称", "生命", "攻击", "范围", "位置", "状态"], rows);
}

function renderSelected() {
  const unit = findSelectedUnit();
  if (!unit) {
    el("selectedText").textContent = "点击地图上的战士查看详情。";
    return;
  }
	  el("selectedText").textContent =
	    `ID: ${unit.id}\n名称: ${unit.name}\n队伍: ${unit.teamName}\n生命: ${unit.health}/${unit.maxHealth}\n` +
	    `攻击力: ${unit.attackPower}\n攻击加成: +${unit.attackBonus}\n攻击范围: ${unit.range}\n范围加成: +${unit.rangeBonus}\n` +
	    `位置: (${unit.row}, ${unit.col})\n状态: ${unit.alive ? (unit.defending ? "defending" : "alive") : "defeated"}`;
}

function renderPathDebugStatus() {
  const box = el("pathDebugText");
  if (!box) return;
  const debug = state.pathDebug;
  if (!state.selectedUnitId) {
    box.textContent = "点击单位显示热力图，再点目标格显示路线。";
    return;
  }
  if (!debug) {
    box.textContent = `选中单位: #${state.selectedUnitId}`;
    return;
  }
  if (!debug.available) {
    box.textContent = debug.message || "当前单位没有 A* 调试数据。";
    return;
  }
  if (!debug.target) {
    box.textContent = `选中单位: #${debug.unitId}\n热力图: ${debug.minCost} - ${debug.maxCost}`;
    return;
  }
  box.textContent =
    `选中单位: #${debug.unitId}\n目标: (${debug.target.row}, ${debug.target.col})\n` +
    `可达: ${debug.reachable ? "yes" : "no"}\n总代价: ${debug.totalCost}\n` +
    `路径长度: ${debug.path.length}\n热力图: ${debug.minCost} - ${debug.maxCost}`;
}

function findSelectedUnit() {
  if (!state.selectedUnitId || !state.data) return null;
  for (const team of state.data.teams) {
    for (const unit of team.units) {
      if (unit.id === state.selectedUnitId) return unit;
    }
  }
  return null;
}

function renderTable(table, headers, rows) {
  table.innerHTML = "";
  const thead = document.createElement("thead");
  thead.innerHTML = `<tr>${headers.map(h => `<th>${h}</th>`).join("")}</tr>`;
  const tbody = document.createElement("tbody");
  tbody.innerHTML = rows.map(row => `<tr>${row.map(v => `<td>${v}</td>`).join("")}</tr>`).join("");
  table.append(thead, tbody);
}

async function loadStrategies() {
  const result = await api("/api/strategies");
  state.strategies = result.strategies;
  renderConfig("matchConfig", defaultConfig);
}

function renderConfig(containerId, values) {
  const box = el(containerId);
  box.innerHTML = "";
  for (const key of teamKeys) {
    const label = document.createElement("label");
    label.textContent = teamLabels[key];
    const select = document.createElement("select");
    select.id = `${containerId}-${key}`;
    for (const name of state.strategies) {
      const option = document.createElement("option");
      option.value = name;
      option.textContent = name;
      const selected = state.strategies.includes(values[key]) ? values[key] : state.strategies[0];
      if (name === selected) option.selected = true;
      select.appendChild(option);
    }
    box.append(label, select);
  }
}

function readConfig(containerId) {
  const config = {};
  for (const key of teamKeys) config[key] = el(`${containerId}-${key}`).value;
  return config;
}

async function runBatch(config, matches, tableId, detailId, chartId, progressId, buttonId, useDefaultConfig) {
  clearBatchTimer(progressId);
  setBatchProgress(progressId, 0, matches, "准备开始");
  el(detailId).textContent = `准备连测 ${matches} 局...`;
  if (buttonId) el(buttonId).disabled = true;

  try {
    const startPath = useDefaultConfig ? "/api/batch-default/start" : "/api/batch/start";
    const started = await api(startPath, { ...config, matches });
    const poll = async () => {
      try {
        const status = await api("/api/batch/status", { jobId: started.jobId });
        renderBatchStatus(status, tableId, detailId, chartId, progressId);
        if (!status.found || status.done || status.error) {
          clearBatchTimer(progressId);
          if (buttonId) el(buttonId).disabled = false;
        }
      } catch (error) {
        clearBatchTimer(progressId);
        if (buttonId) el(buttonId).disabled = false;
        el(detailId).textContent = error.message;
      }
    };
    await poll();
    state.batchTimers[progressId] = setInterval(poll, 200);
  } catch (error) {
    clearBatchTimer(progressId);
    if (buttonId) el(buttonId).disabled = false;
    el(detailId).textContent = error.message;
  }
}

function renderBatchResult(result, tableId, detailId, chartId) {
  renderTable(el(tableId),
    ["队伍", "胜场", "胜率", "均分", "最高", "最低", "总分", "伤害", "击败", "道具", "回血", "扣分", "无效", "无进展"],
    result.teams.map(t => [
      t.teamName, t.wins, `${t.winRate.toFixed(1)}%`, t.averageScore.toFixed(1),
      t.bestScore, t.worstScore, t.totalScore, t.damageDealt, t.defeats,
      t.powerUps, t.healingDone, t.penaltyScore, t.invalidActions, t.noProgressPenalties
    ]));
  renderBatchChart(result, chartId);
  el(detailId).textContent = `完成 ${result.matches} 局；无胜者 ${result.noWinnerMatches} 局；超过保护上限未结束 ${result.unfinishedMatches} 局。`;
}

function renderBatchStatus(status, tableId, detailId, chartId, progressId) {
  if (!status.found) {
    el(detailId).textContent = status.message || "连测任务不存在。";
    setBatchProgress(progressId, 0, 1, "未找到任务");
    return;
  }
  if (status.result) {
    renderBatchResult(status.result, tableId, detailId, chartId);
  }
  const label = status.error
    ? "发生错误"
    : (status.done ? "完成" : "运行中");
  setBatchProgress(progressId, status.completed, status.total, label);
  const percent = status.total > 0 ? Math.floor(status.completed / status.total * 100) : 0;
  const lines = [
    `${label}: ${status.completed} / ${status.total} (${percent}%)`
  ];
  if (status.result) {
    lines.push(`无胜者 ${status.result.noWinnerMatches} 局；超过保护上限未结束 ${status.result.unfinishedMatches} 局。`);
  }
  if (status.error) {
    lines.push(status.error);
  }
  el(detailId).textContent = lines.join("\n");
}

function setBatchProgress(progressId, completed, total, label) {
  const progress = el(progressId);
  if (!progress) return;
  progress.hidden = false;
  const percent = total > 0 ? Math.max(0, Math.min(100, completed / total * 100)) : 0;
  progress.querySelector("i").style.width = `${percent}%`;
  progress.querySelector("span").textContent = `${label} ${completed} / ${total}`;
}

function clearBatchTimer(progressId) {
  if (state.batchTimers[progressId]) {
    clearInterval(state.batchTimers[progressId]);
    state.batchTimers[progressId] = null;
  }
}

function renderBatchChart(result, chartId) {
  const chart = el(chartId);
  if (!chart) return;
  const maxWins = Math.max(1, ...result.teams.map(t => t.wins));
  chart.innerHTML = result.teams.map(t => `
    <div class="barRow">
      <span>${t.teamName}</span>
      <span class="barTrack"><i class="barFill" style="width:${t.wins / maxWins * 100}%"></i></span>
      <span>${t.wins} 胜</span>
    </div>`).join("");
}

async function loadProjectFiles() {
  const result = await api("/api/project/files");
  const list = el("fileList");
  list.innerHTML = "";
  for (const file of result.files) {
    const item = document.createElement("div");
    item.className = "fileItem";
    item.innerHTML = `<span class="badge">${file.group}</span><span title="${file.description}">${file.path}</span><button>打开</button>`;
    item.querySelector("button").addEventListener("click", async () => {
      await api("/api/project/open", { path: file.path });
      el("projectOutput").textContent = `已打开: ${file.path}`;
    });
    list.appendChild(item);
  }
}

function drawCurve() {
  const svg = el("curveSvg");
  const mid = Number(el("healthMid").value) / 100;
  const steep = Number(el("healthSteep").value);
  const points = [];
  for (let i = 0; i <= 100; i++) {
    const x = i / 100;
    const y = 1 / (1 + Math.exp((x - mid) * steep * 2));
    points.push(`${30 + x * 300},${150 - y * 120}`);
  }
  svg.innerHTML = `
    <line x1="30" y1="150" x2="330" y2="150" stroke="#cbd5df"/>
    <line x1="30" y1="30" x2="30" y2="150" stroke="#cbd5df"/>
    <polyline fill="none" stroke="#2563a8" stroke-width="3" points="${points.join(" ")}"/>
    <text x="30" y="170" font-size="12" fill="#637083">healthRate</text>
    <text x="250" y="28" font-size="12" fill="#637083">healing utility</text>`;
}

function bindUi() {
  el("stepBtn").addEventListener("click", async () => { state.data = await api("/api/step", {}); state.pathDebug = null; render(); });
  el("roundBtn").addEventListener("click", async () => { state.data = await api("/api/round", {}); state.pathDebug = null; render(); });
  el("resetBtn").addEventListener("click", async () => { state.data = await api("/api/reset", {}); state.selectedUnitId = null; state.pathDebug = null; render(); });
  el("autoBtn").addEventListener("click", () => {
    if (state.autoTimer) {
      clearInterval(state.autoTimer);
      state.autoTimer = null;
      el("autoBtn").textContent = "自动播放";
    } else {
      state.autoTimer = setInterval(async () => {
      state.data = await api("/api/step", {});
      state.pathDebug = null;
      render();
      if (state.data.gameOver) el("autoBtn").click();
      }, Number(el("speedRange").value));
      el("autoBtn").textContent = "暂停";
    }
  });
  el("speedRange").addEventListener("input", () => {
    if (state.autoTimer) {
      el("autoBtn").click();
      el("autoBtn").click();
    }
  });
  for (const tab of document.querySelectorAll(".tab")) {
    tab.addEventListener("click", () => {
      document.querySelectorAll(".tab,.tabPage").forEach(n => n.classList.remove("active"));
      tab.classList.add("active");
      el(tab.dataset.tab).classList.add("active");
    });
  }
  el("clearPathBtn").addEventListener("click", clearPathDebug);
  el("refreshStrategiesBtn").addEventListener("click", loadStrategies);
  el("loadMatchBtn").addEventListener("click", async () => { state.data = await api("/api/load", readConfig("matchConfig")); state.selectedUnitId = null; state.pathDebug = null; render(); });
  el("runMatchBtn").addEventListener("click", () => runBatch(readConfig("matchConfig"), Number(el("matchCount").value), "matchTable", "matchDetail", "matchChart", "matchProgress", "runMatchBtn", false));
  el("openSrcBtn").addEventListener("click", async () => {
    await api("/api/project/open", { path: "src" });
  });
  el("runDefaultBatchBtn").addEventListener("click", async () => {
    await runBatch({}, Number(el("defaultBatchCount").value), "batchTable", "batchDetail", "batchChart", "batchProgress", "runDefaultBatchBtn", true);
  });
  el("refreshFilesBtn").addEventListener("click", loadProjectFiles);
  el("openProjectBtn").addEventListener("click", async () => {
    await api("/api/project/open", { path: "root" });
    el("projectOutput").textContent = "已打开项目文件夹。";
  });
  el("compileBtn").addEventListener("click", async () => {
    el("projectOutput").textContent = "正在编译...";
    const result = await api("/api/project/compile", {});
    el("projectOutput").textContent = result.output;
  });
  el("compileRestartBtn").addEventListener("click", async () => {
    el("projectOutput").textContent = "正在编译并准备重启 Web 调试台...";
    const result = await api("/api/project/compile-restart", {});
    el("projectOutput").textContent = result.output;
  });
  el("healthMid").addEventListener("input", drawCurve);
  el("healthSteep").addEventListener("input", drawCurve);
}

async function boot() {
  bindUi();
  drawCurve();
  await loadStrategies();
  await loadProjectFiles();
  await refreshState();
}

boot().catch(error => {
  el("resultText").textContent = error.message;
});
