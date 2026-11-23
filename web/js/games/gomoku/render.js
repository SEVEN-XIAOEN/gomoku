/* ------------------------------------------------------------------
   五子棋 Canvas 渲染器（纯展示，不含任何规则判断）
   ------------------------------------------------------------------ */
(function () {
    'use strict';

    window.QP = window.QP || {};

    var STAR_POINTS = {
        9: [[2, 2], [2, 6], [6, 2], [6, 6], [4, 4]],
        13: [[3, 3], [3, 9], [9, 3], [9, 9], [6, 6]],
        15: [[3, 3], [3, 11], [11, 3], [11, 11], [7, 7]]
    };

    function create(canvas) {
        var ctx = canvas.getContext('2d');
        var metrics = null;
        var state = null;
        var opts = {};

        function layout() {
            var rect = canvas.getBoundingClientRect();
            var side = rect.width || 600;
            var dpr = window.devicePixelRatio || 1;
            var pixels = Math.max(1, Math.round(side * dpr));
            if (canvas.width !== pixels || canvas.height !== pixels) {
                canvas.width = pixels;
                canvas.height = pixels;
            }
            var scale = pixels / side;
            ctx.setTransform(scale, 0, 0, scale, 0, 0);

            var pad = side * 0.055;
            var cell = (side - pad * 2) / Math.max(1, state.size - 1);
            metrics = { side: side, pad: pad, cell: cell };
            return metrics;
        }

        function pointOf(r, c) {
            return {
                x: metrics.pad + c * metrics.cell,
                y: metrics.pad + r * metrics.cell
            };
        }

        function drawWood(side) {
            var gradient = ctx.createLinearGradient(0, 0, side, side);
            gradient.addColorStop(0, '#f0d9a8');
            gradient.addColorStop(0.5, '#e8c98d');
            gradient.addColorStop(1, '#dcb877');
            ctx.fillStyle = gradient;
            ctx.fillRect(0, 0, side, side);

            ctx.save();
            ctx.globalAlpha = 0.07;
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
            var start = metrics.pad;
            var end = metrics.pad + (n - 1) * metrics.cell;

            ctx.save();
            ctx.strokeStyle = 'rgba(60, 40, 18, 0.72)';
            ctx.lineWidth = 1;
            for (var i = 0; i < n; i++) {
                var offset = metrics.pad + i * metrics.cell;
                ctx.beginPath();
                ctx.moveTo(start, offset);
                ctx.lineTo(end, offset);
                ctx.stroke();

                ctx.beginPath();
                ctx.moveTo(offset, start);
                ctx.lineTo(offset, end);
                ctx.stroke();
            }
            ctx.lineWidth = 2;
            ctx.strokeRect(start, start, end - start, end - start);
            ctx.restore();
        }

        function drawStars() {
            var stars = STAR_POINTS[state.size] || [];
            var radius = Math.max(2, metrics.cell * 0.11);
            ctx.save();
            ctx.fillStyle = 'rgba(45, 30, 12, 0.82)';
            stars.forEach(function (p) {
                var pos = pointOf(p[0], p[1]);
                ctx.beginPath();
                ctx.arc(pos.x, pos.y, radius, 0, Math.PI * 2);
                ctx.fill();
            });
            ctx.restore();
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

        function drawStones() {
            var pending = opts.pending;
            for (var r = 0; r < state.size; r++) {
                for (var c = 0; c < state.size; c++) {
                    var color = state.board[r][c];
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

        function drawHighlights() {
            var radius = metrics.cell * 0.13;

            if (state.lastMove && state.board[state.lastMove.r]) {
                var last = pointOf(state.lastMove.r, state.lastMove.c);
                var lastColor = state.board[state.lastMove.r][state.lastMove.c];
                ctx.save();
                ctx.beginPath();
                ctx.arc(last.x, last.y, radius, 0, Math.PI * 2);
                ctx.fillStyle = lastColor === 1 ? '#e74c3c' : '#2f6f5e';
                ctx.fill();
                ctx.restore();
            }

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

                var a = pointOf(state.winLine[0][0], state.winLine[0][1]);
                var b = pointOf(
                    state.winLine[state.winLine.length - 1][0],
                    state.winLine[state.winLine.length - 1][1]
                );
                ctx.globalAlpha = 0.55;
                ctx.beginPath();
                ctx.moveTo(a.x, a.y);
                ctx.lineTo(b.x, b.y);
                ctx.stroke();
                ctx.restore();
            }
        }

        function draw(nextState, nextOpts) {
            if (!nextState || !nextState.board || !nextState.board.length) {
                return;
            }
            state = nextState;
            opts = nextOpts || {};
            var m = layout();

            ctx.clearRect(0, 0, m.side, m.side);
            drawWood(m.side);
            drawGrid();
            drawStars();

            if (opts.hover && opts.canPlay && state.board[opts.hover.r][opts.hover.c] === 0) {
                drawStone(opts.hover.r, opts.hover.c, opts.previewColor || 1, 0.42, false, 1);
            }

            drawStones();
            drawHighlights();
        }

        function locate(clientX, clientY) {
            if (!metrics || !state) {
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
            return distance <= metrics.cell * 0.5 ? { r: r, c: c } : null;
        }

        return {
            draw: draw,
            locate: locate,
            redraw: function () {
                draw(state, opts);
            }
        };
    }

    window.QP.gomokuRender = { create: create };
})();
