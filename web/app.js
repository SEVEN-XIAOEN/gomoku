/* ------------------------------------------------------------------
   网页五子棋前端逻辑
   职责：把棋盘画到 Canvas 上、把点击翻译成 /api/move 请求、渲染后端返回的状态。
   规则判定和电脑 AI 全部在 Java 后端完成，这里不做任何棋力计算。
   ------------------------------------------------------------------ */

(function () {
    'use strict';

    var canvas = document.getElementById('board');
    var ctx = canvas.getContext('2d');
    var overlay = document.getElementById('overlay');
    var overlayText = document.getElementById('overlay-text');
    var messageEl = document.getElementById('message');
    var movesEl = document.getElementById('moves');
    var aiMillisEl = document.getElementById('ai-millis');
    var sizeSelect = document.getElementById('size');
    var difficultySelect = document.getElementById('difficulty');
    var newGameBtn = document.getElementById('new-game');
    var undoBtn = document.getElementById('undo');

    /** 星位（让棋手定位用的黑点），按棋盘大小给出 */
    var STAR_POINTS = {
        9: [[2, 2], [2, 6], [6, 2], [6, 6], [4, 4]],
        13: [[3, 3], [3, 9], [9, 3], [9, 9], [6, 6]],
        15: [[3, 3], [3, 11], [11, 3], [11, 11], [7, 7]]
    };

    /** 后端返回的完整对局状态 */
    var state = {
        size: 15,
        board: [],
        status: 'playing',
        playerColor: 1,
        aiColor: 2,
        lastMove: null,
        moves: 0,
        difficulty: 2,
        message: '',
        aiMillis: 0,
        winLine: null
    };

    var hover = null;          // 鼠标悬停的交叉点 {r, c}，不在棋盘上时为 null
    var busy = false;          // 是否有请求还在路上
    var metrics = null;        // 绘制几何信息，供点击换算使用
    var pending = null;        // 刚落下的棋子，用于出现动画

    // ---------------------------------------------------------------- 网络

    function request(url, options) {
        return fetch(url, options).then(function (response) {
            return response.json().catch(function () {
                return null;
            }).then(function (data) {
                if (!response.ok) {
                    throw new Error((data && data.error) || ('请求失败（HTTP ' + response.status + '）'));
                }
                return data;
            });
        });
    }

    function postJson(url, payload) {
        return request(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload || {})
        });
    }

    // ---------------------------------------------------------------- 状态渲染

    function applyState(data) {
        if (!data) {
            return;
        }
        state = data;
        hover = null;
        sizeSelect.value = String(state.size);
        difficultySelect.value = String(state.difficulty);
        movesEl.textContent = String(state.moves);
        aiMillisEl.textContent = state.aiMillis + ' ms';

        var tone = 'info';
        if (state.status === 'black_win') {
            tone = 'win';
        } else if (state.status === 'white_win') {
            tone = 'lose';
        } else if (state.status === 'draw') {
            tone = 'info';
        }
        setMessage(state.message, tone);

        var finished = state.status !== 'playing';
        canvas.classList.toggle('board--disabled', finished || busy);
        undoBtn.disabled = busy || state.moves === 0;
        newGameBtn.disabled = busy;
        sizeSelect.disabled = busy;
        difficultySelect.disabled = busy;

        render();
    }

    function setMessage(text, tone) {
        messageEl.textContent = text || '';
        if (tone && tone !== 'info') {
            messageEl.dataset.tone = tone;
        } else {
            delete messageEl.dataset.tone;
        }
    }

    function setBusy(value, text) {
        busy = value;
        if (value) {
            overlayText.textContent = text || '处理中…';
            overlay.hidden = false;
        } else {
            overlay.hidden = true;
        }
        canvas.classList.toggle('board--disabled', value || state.status !== 'playing');
    }

    // ---------------------------------------------------------------- 绘制

    /**
     * 根据画布当前显示尺寸重算分辨率，保证在高分屏上也清晰。
     * 返回棋盘左上角内边距与格距（单位：CSS 像素）。
     */
    function layout() {
        var rect = canvas.getBoundingClientRect();
        var side = Math.round(rect.width || 600);
        var dpr = window.devicePixelRatio || 1;

        if (canvas.width !== Math.round(side * dpr) || canvas.height !== Math.round(side * dpr)) {
            canvas.width = Math.round(side * dpr);
            canvas.height = Math.round(side * dpr);
        }
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

        var pad = side * 0.052;
        var cell = (side - pad * 2) / (state.size - 1);
        metrics = {side: side, pad: pad, cell: cell};
        return metrics;
    }

    function pointOf(r, c) {
        return {
            x: metrics.pad + c * metrics.cell,
            y: metrics.pad + r * metrics.cell
        };
    }

    function render() {
        if (!state.board || !state.board.length) {
            return;
        }
        var m = layout();
        var side = m.side;

        ctx.clearRect(0, 0, side, side);
        drawWood(side);
        drawGrid();
        drawStars();
        drawStones();
        drawHighlights();
        if (hover && state.status === 'playing' && !busy) {
            drawStone(hover.r, hover.c, state.playerColor, 0.42, false);
        }
    }

    function drawWood(side) {
        var base = ctx.createLinearGradient(0, 0, side, side);
        base.addColorStop(0, '#f0d9a8');
        base.addColorStop(0.5, '#e8c98d');
        base.addColorStop(1, '#dcb877');
        ctx.fillStyle = base;
        ctx.fillRect(0, 0, side, side);

        // 几道很淡的木纹，让棋盘不至于太平
        ctx.save();
        ctx.globalAlpha = 0.08;
        ctx.strokeStyle = '#7a5322';
        for (var i = 0; i < 9; i++) {
            var y = (i + 0.5) * side / 9 + Math.sin(i * 1.7) * 6;
            ctx.lineWidth = 1 + (i % 3);
            ctx.beginPath();
            ctx.moveTo(0, y);
            ctx.bezierCurveTo(side * 0.33, y + 9, side * 0.66, y - 9, side, y + 3);
            ctx.stroke();
        }
        ctx.restore();
    }

    function drawGrid() {
        var n = state.size;
        var m = metrics;

        ctx.save();
        ctx.strokeStyle = 'rgba(60, 40, 18, 0.72)';
        ctx.lineWidth = 1;

        for (var i = 0; i < n; i++) {
            var offset = m.pad + i * m.cell;
            var end = m.pad + (n - 1) * m.cell;

            ctx.beginPath();
            ctx.moveTo(m.pad, offset);
            ctx.lineTo(end, offset);
            ctx.stroke();

            ctx.beginPath();
            ctx.moveTo(offset, m.pad);
            ctx.lineTo(offset, end);
            ctx.stroke();
        }

        // 外框加粗一点，视觉上更稳
        ctx.lineWidth = 2;
        ctx.strokeRect(m.pad, m.pad, m.cell * (n - 1), m.cell * (n - 1));
        ctx.restore();
    }

    function drawStars() {
        var points = STAR_POINTS[state.size] || [];
        var radius = Math.max(2, metrics.cell * 0.11);
        ctx.save();
        ctx.fillStyle = 'rgba(45, 30, 12, 0.82)';
        points.forEach(function (p) {
            var pos = pointOf(p[0], p[1]);
            ctx.beginPath();
            ctx.arc(pos.x, pos.y, radius, 0, Math.PI * 2);
            ctx.fill();
        });
        ctx.restore();
    }

    function drawStones() {
        var board = state.board;
        for (var r = 0; r < state.size; r++) {
            for (var c = 0; c < state.size; c++) {
                var color = board[r][c];
                if (!color) {
                    continue;
                }
                var scale = 1;
                if (pending && pending.r === r && pending.c === c) {
                    var progress = Math.min(1, (Date.now() - pending.start) / 180);
                    scale = 0.55 + 0.45 * progress + 0.06 * Math.sin(progress * Math.PI);
                }
                drawStone(r, c, color, 1, true, scale);
            }
        }
    }

    function drawStone(r, c, color, alpha, withShadow, scale) {
        var pos = pointOf(r, c);
        var radius = metrics.cell * 0.44 * (scale || 1);

        ctx.save();
        ctx.globalAlpha = alpha;

        if (withShadow) {
            ctx.shadowColor = 'rgba(60, 40, 15, 0.35)';
            ctx.shadowBlur = metrics.cell * 0.18;
            ctx.shadowOffsetY = metrics.cell * 0.06;
        }

        var gradient = ctx.createRadialGradient(
            pos.x - radius * 0.32, pos.y - radius * 0.34, radius * 0.1,
            pos.x, pos.y, radius
        );
        if (color === 1) {
            gradient.addColorStop(0, '#787878');
            gradient.addColorStop(0.55, '#1b1b1b');
            gradient.addColorStop(1, '#000000');
        } else {
            gradient.addColorStop(0, '#ffffff');
            gradient.addColorStop(0.6, '#f1f0ec');
            gradient.addColorStop(1, '#cdc7b8');
        }

        ctx.beginPath();
        ctx.arc(pos.x, pos.y, radius, 0, Math.PI * 2);
        ctx.fillStyle = gradient;
        ctx.fill();

        ctx.shadowColor = 'transparent';
        ctx.shadowBlur = 0;
        ctx.shadowOffsetY = 0;
        ctx.lineWidth = 1;
        ctx.strokeStyle = color === 1 ? 'rgba(0,0,0,0.6)' : 'rgba(150,140,120,0.75)';
        ctx.stroke();

        ctx.restore();
    }

    function drawHighlights() {
        var radius = metrics.cell * 0.13;

        // 最近一手：中间点一个小标记
        if (state.lastMove) {
            var last = pointOf(state.lastMove.r, state.lastMove.c);
            var lastColor = state.board[state.lastMove.r][state.lastMove.c];
            ctx.save();
            ctx.beginPath();
            ctx.arc(last.x, last.y, radius, 0, Math.PI * 2);
            ctx.fillStyle = lastColor === 1 ? '#e74c3c' : '#2f6f5e';
            ctx.fill();
            ctx.restore();
        }

        // 获胜的五连：套一圈高亮，再画一条连线
        if (state.winLine && state.winLine.length) {
            ctx.save();
            ctx.strokeStyle = 'rgba(231, 76, 60, 0.95)';
            ctx.lineWidth = Math.max(2, metrics.cell * 0.07);

            state.winLine.forEach(function (p) {
                var pos = pointOf(p[0], p[1]);
                ctx.beginPath();
                ctx.arc(pos.x, pos.y, metrics.cell * 0.46, 0, Math.PI * 2);
                ctx.stroke();
            });

            var first = state.winLine[0];
            var lastPoint = state.winLine[state.winLine.length - 1];
            var a = pointOf(first[0], first[1]);
            var b = pointOf(lastPoint[0], lastPoint[1]);
            ctx.globalAlpha = 0.55;
            ctx.beginPath();
            ctx.moveTo(a.x, a.y);
            ctx.lineTo(b.x, b.y);
            ctx.stroke();
            ctx.restore();
        }
    }

    // ---------------------------------------------------------------- 交互

    /** 把鼠标/触摸位置换算成最近的交叉点；离得太远返回 null */
    function locate(clientX, clientY) {
        if (!metrics) {
            return null;
        }
        var rect = canvas.getBoundingClientRect();
        var x = clientX - rect.left;
        var y = clientY - rect.top;

        var c = Math.round((x - metrics.pad) / metrics.cell);
        var r = Math.round((y - metrics.pad) / metrics.cell);
        if (r < 0 || c < 0 || r >= state.size || c >= state.size) {
            return null;
        }

        var pos = pointOf(r, c);
        var distance = Math.hypot(pos.x - x, pos.y - y);
        return distance <= metrics.cell * 0.5 ? {r: r, c: c} : null;
    }

    function canPlay() {
        return !busy && state.status === 'playing';
    }

    function play(r, c) {
        if (!canPlay()) {
            return;
        }
        if (!state.board[r] || state.board[r][c] !== 0) {
            return;
        }
        resolveMove(r, c);
    }

    function resolveMove(r, c) {
        setBusy(true, '电脑思考中…');
        postJson('/api/move', {r: r, c: c})
            .then(function (data) {
                setBusy(false);
                pending = {r: r, c: c, start: Date.now()};
                applyState(data);
                animate();
            })
            .catch(function (error) {
                setBusy(false);
                setMessage(error.message, 'error');
                render();
            });
    }

    function animate() {
        if (!pending) {
            return;
        }
        render();
        if (Date.now() - pending.start < 200) {
            window.requestAnimationFrame(animate);
        } else {
            pending = null;
            render();
        }
    }

    canvas.addEventListener('mousemove', function (event) {
        var next = canPlay() ? locate(event.clientX, event.clientY) : null;
        var changed = (!!next !== !!hover) || (next && hover && (next.r !== hover.r || next.c !== hover.c));
        if (changed) {
            hover = next;
            render();
        }
    });

    canvas.addEventListener('mouseleave', function () {
        if (hover) {
            hover = null;
            render();
        }
    });

    canvas.addEventListener('click', function (event) {
        var target = locate(event.clientX, event.clientY);
        if (!target) {
            return;
        }
        if (state.status !== 'playing') {
            setMessage('这一局已经结束了，点「新对局」再来一盘吧', 'error');
            return;
        }
        if (state.board[target.r][target.c] !== 0) {
            setMessage('这个位置已经有棋子啦', 'error');
            return;
        }
        play(target.r, target.c);
    });

    newGameBtn.addEventListener('click', function () {
        var size = parseInt(sizeSelect.value, 10);
        setBusy(true, '正在开局…');
        postJson('/api/new', {size: size})
            .then(function (data) {
                setBusy(false);
                pending = null;
                applyState(data);
            })
            .catch(function (error) {
                setBusy(false);
                setMessage(error.message, 'error');
            });
    });

    undoBtn.addEventListener('click', function () {
        setBusy(true, '正在悔棋…');
        postJson('/api/undo', {})
            .then(function (data) {
                setBusy(false);
                pending = null;
                applyState(data);
            })
            .catch(function (error) {
                setBusy(false);
                setMessage(error.message, 'error');
            });
    });

    difficultySelect.addEventListener('change', function () {
        var level = parseInt(difficultySelect.value, 10);
        setBusy(true, '正在切换难度…');
        postJson('/api/difficulty', {level: level})
            .then(function (data) {
                setBusy(false);
                applyState(data);
            })
            .catch(function (error) {
                setBusy(false);
                setMessage(error.message, 'error');
            });
    });

    // 窗口尺寸变化（或手机旋转）时重画，棋盘始终铺满容器
    if (window.ResizeObserver) {
        new ResizeObserver(function () {
            render();
        }).observe(canvas);
    } else {
        window.addEventListener('resize', render);
    }

    // ---------------------------------------------------------------- 启动

    request('/api/state')
        .then(applyState)
        .catch(function (error) {
            setMessage('连接后端失败：' + error.message, 'error');
        });
})();
