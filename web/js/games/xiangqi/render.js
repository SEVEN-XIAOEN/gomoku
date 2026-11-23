/* ------------------------------------------------------------------
   中国象棋 Canvas 渲染器（纯展示）
   9 列 × 10 行，红方固定显示在下方。棋子编码 = 颜色 * 10 + 类型。
   ------------------------------------------------------------------ */
(function () {
    'use strict';

    window.QP = window.QP || {};

    var LABELS = {
        1: { 1: '帅', 2: '仕', 3: '相', 4: '马', 5: '车', 6: '炮', 7: '兵' },
        2: { 1: '将', 2: '士', 3: '象', 4: '马', 5: '车', 6: '炮', 7: '卒' }
    };

    function colorOf(piece) {
        return Math.floor(piece / 10);
    }

    function typeOf(piece) {
        return piece % 10;
    }

    function create(canvas) {
        var ctx = canvas.getContext('2d');
        var metrics = null;
        var state = null;
        var opts = {};

        function layout() {
            var rect = canvas.getBoundingClientRect();
            var width = rect.width || 540;
            var height = rect.height || width * 10 / 9;
            var dpr = window.devicePixelRatio || 1;
            var pixelWidth = Math.max(1, Math.round(width * dpr));
            var pixelHeight = Math.max(1, Math.round(height * dpr));

            if (canvas.width !== pixelWidth || canvas.height !== pixelHeight) {
                canvas.width = pixelWidth;
                canvas.height = pixelHeight;
            }
            ctx.setTransform(pixelWidth / width, 0, 0, pixelHeight / height, 0, 0);

            var padX = width * 0.078;
            var cell = (width - padX * 2) / 8;
            var boardHeight = cell * 9;
            var padY = (height - boardHeight) / 2;
            metrics = {
                width: width,
                height: height,
                padX: padX,
                padY: padY,
                cell: cell
            };
            return metrics;
        }

        function pointOf(r, c) {
            return {
                x: metrics.padX + c * metrics.cell,
                y: metrics.padY + r * metrics.cell
            };
        }

        function line(x1, y1, x2, y2) {
            ctx.beginPath();
            ctx.moveTo(x1, y1);
            ctx.lineTo(x2, y2);
            ctx.stroke();
        }

        function drawWood() {
            var gradient = ctx.createLinearGradient(0, 0, metrics.width, metrics.height);
            gradient.addColorStop(0, '#f2ddb2');
            gradient.addColorStop(0.5, '#e9cd97');
            gradient.addColorStop(1, '#dfbd80');
            ctx.fillStyle = gradient;
            ctx.fillRect(0, 0, metrics.width, metrics.height);

            ctx.save();
            ctx.globalAlpha = 0.06;
            ctx.strokeStyle = '#7a5322';
            for (var i = 0; i < 12; i++) {
                var x = (i + 0.5) * metrics.width / 12;
                ctx.lineWidth = 1 + (i % 3);
                ctx.beginPath();
                ctx.moveTo(x, 0);
                ctx.bezierCurveTo(x + 7, metrics.height * 0.33, x - 7, metrics.height * 0.66, x + 2, metrics.height);
                ctx.stroke();
            }
            ctx.restore();
        }

        function drawGrid() {
            var cell = metrics.cell;
            var x0 = metrics.padX;
            var x8 = x0 + cell * 8;
            var y0 = metrics.padY;
            var y9 = y0 + cell * 9;

            ctx.save();
            ctx.strokeStyle = 'rgba(62, 41, 18, 0.78)';
            ctx.lineWidth = 1.15;

            for (var r = 0; r < 10; r++) {
                var y = y0 + r * cell;
                line(x0, y, x8, y);
            }
            for (var c = 0; c < 9; c++) {
                var x = x0 + c * cell;
                if (c === 0 || c === 8) {
                    line(x, y0, x, y9);
                } else {
                    // 中间七路在楚河汉界处断开
                    line(x, y0, x, y0 + cell * 4);
                    line(x, y0 + cell * 5, x, y9);
                }
            }

            ctx.lineWidth = 2.2;
            ctx.strokeRect(x0, y0, cell * 8, cell * 9);
            ctx.restore();
        }

        function drawPalaces() {
            var cell = metrics.cell;
            ctx.save();
            ctx.strokeStyle = 'rgba(62, 41, 18, 0.72)';
            ctx.lineWidth = 1.1;

            line(pointOf(0, 3).x, pointOf(0, 3).y, pointOf(2, 5).x, pointOf(2, 5).y);
            line(pointOf(0, 5).x, pointOf(0, 5).y, pointOf(2, 3).x, pointOf(2, 3).y);
            line(pointOf(7, 3).x, pointOf(7, 3).y, pointOf(9, 5).x, pointOf(9, 5).y);
            line(pointOf(7, 5).x, pointOf(7, 5).y, pointOf(9, 3).x, pointOf(9, 3).y);
            ctx.restore();

            void cell;
        }

        function riverText(text, centerX, y) {
            var step = metrics.cell * 0.62;
            var chars = text.split('');
            ctx.save();
            ctx.fillStyle = 'rgba(96, 64, 30, 0.58)';
            ctx.font = '600 ' + (metrics.cell * 0.46) + 'px "KaiTi","STKaiti","SimSun",serif';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            chars.forEach(function (ch, index) {
                ctx.fillText(ch, centerX + (index - (chars.length - 1) / 2) * step, y);
            });
            ctx.restore();
        }

        function drawRiver() {
            var y = (pointOf(4, 0).y + pointOf(5, 0).y) / 2;
            riverText('楚河', (pointOf(0, 1).x + pointOf(0, 3).x) / 2, y);
            riverText('汉界', (pointOf(0, 5).x + pointOf(0, 7).x) / 2, y);
        }

        function drawPiece(r, c, piece) {
            var pos = pointOf(r, c);
            var radius = metrics.cell * 0.43;
            var color = colorOf(piece);

            ctx.save();
            ctx.shadowColor = 'rgba(70, 46, 16, 0.35)';
            ctx.shadowBlur = metrics.cell * 0.16;
            ctx.shadowOffsetY = metrics.cell * 0.06;

            var body = ctx.createRadialGradient(
                pos.x - radius * 0.35, pos.y - radius * 0.38, radius * 0.1,
                pos.x, pos.y, radius
            );
            body.addColorStop(0, '#fffdf4');
            body.addColorStop(0.62, '#f5e7c6');
            body.addColorStop(1, '#dfc69a');
            ctx.beginPath();
            ctx.arc(pos.x, pos.y, radius, 0, Math.PI * 2);
            ctx.fillStyle = body;
            ctx.fill();

            ctx.shadowColor = 'transparent';
            ctx.shadowBlur = 0;
            ctx.shadowOffsetY = 0;

            ctx.lineWidth = Math.max(1.4, metrics.cell * 0.035);
            ctx.strokeStyle = color === 1 ? 'rgba(168, 52, 38, 0.9)' : 'rgba(40, 36, 30, 0.9)';
            ctx.stroke();

            ctx.beginPath();
            ctx.arc(pos.x, pos.y, radius * 0.84, 0, Math.PI * 2);
            ctx.lineWidth = 1;
            ctx.strokeStyle = color === 1 ? 'rgba(168, 52, 38, 0.45)' : 'rgba(40, 36, 30, 0.4)';
            ctx.stroke();

            ctx.fillStyle = color === 1 ? '#a83426' : '#241f1a';
            ctx.font = '700 ' + (metrics.cell * 0.52) + 'px "KaiTi","STKaiti","SimSun",serif';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText(LABELS[color][typeOf(piece)] || '?', pos.x, pos.y + metrics.cell * 0.02);
            ctx.restore();
        }

        function drawPieces() {
            for (var r = 0; r < 10; r++) {
                for (var c = 0; c < 9; c++) {
                    var piece = state.board[r][c];
                    if (piece) {
                        drawPiece(r, c, piece);
                    }
                }
            }
        }

        function cornerMarks(r, c, color) {
            var pos = pointOf(r, c);
            var radius = metrics.cell * 0.5;
            var length = metrics.cell * 0.16;
            var gap = metrics.cell * 0.06;

            ctx.save();
            ctx.strokeStyle = color;
            ctx.lineWidth = Math.max(2, metrics.cell * 0.055);
            ctx.lineCap = 'round';
            [[-1, -1], [1, -1], [-1, 1], [1, 1]].forEach(function (dir) {
                var x = pos.x + dir[0] * radius;
                var y = pos.y + dir[1] * radius;
                ctx.beginPath();
                ctx.moveTo(x - dir[0] * length, y);
                ctx.lineTo(x, y);
                ctx.lineTo(x, y - dir[1] * length);
                ctx.stroke();
            });
            ctx.restore();
            void gap;
        }

        function drawSelection() {
            if (!opts.selected) {
                return;
            }
            var pos = pointOf(opts.selected.r, opts.selected.c);
            ctx.save();
            ctx.strokeStyle = 'rgba(34, 122, 102, 0.95)';
            ctx.lineWidth = Math.max(2.5, metrics.cell * 0.07);
            ctx.beginPath();
            ctx.arc(pos.x, pos.y, metrics.cell * 0.47, 0, Math.PI * 2);
            ctx.stroke();
            ctx.restore();
        }

        function drawLegalMoves() {
            var moves = opts.legal || [];
            moves.forEach(function (move) {
                var pos = pointOf(move.r, move.c);
                var occupied = state.board[move.r][move.c] !== 0;
                ctx.save();
                if (occupied) {
                    ctx.strokeStyle = 'rgba(190, 62, 44, 0.95)';
                    ctx.lineWidth = Math.max(2.2, metrics.cell * 0.06);
                    ctx.beginPath();
                    ctx.arc(pos.x, pos.y, metrics.cell * 0.46, 0, Math.PI * 2);
                    ctx.stroke();
                } else {
                    ctx.fillStyle = 'rgba(34, 122, 102, 0.78)';
                    ctx.beginPath();
                    ctx.arc(pos.x, pos.y, Math.max(3, metrics.cell * 0.1), 0, Math.PI * 2);
                    ctx.fill();
                }
                ctx.restore();
            });
        }

        function findKing(color) {
            for (var r = 0; r < 10; r++) {
                for (var c = 0; c < 9; c++) {
                    var piece = state.board[r][c];
                    if (piece && colorOf(piece) === color && typeOf(piece) === 1) {
                        return { r: r, c: c };
                    }
                }
            }
            return null;
        }

        function drawHighlights() {
            var last = state.lastMove;
            if (last) {
                cornerMarks(last.fr, last.fc, 'rgba(120, 94, 52, 0.6)');
                cornerMarks(last.tr, last.tc, 'rgba(214, 160, 46, 0.98)');
            }

            if (state.check) {
                var king = findKing(state.turn);
                if (king) {
                    var pos = pointOf(king.r, king.c);
                    ctx.save();
                    ctx.strokeStyle = 'rgba(216, 64, 44, 0.95)';
                    ctx.shadowColor = 'rgba(216, 64, 44, 0.8)';
                    ctx.shadowBlur = metrics.cell * 0.4;
                    ctx.lineWidth = Math.max(2.5, metrics.cell * 0.075);
                    ctx.beginPath();
                    ctx.arc(pos.x, pos.y, metrics.cell * 0.5, 0, Math.PI * 2);
                    ctx.stroke();
                    ctx.restore();
                }
            }

            if (opts.hover) {
                var hoverPos = pointOf(opts.hover.r, opts.hover.c);
                ctx.save();
                ctx.strokeStyle = 'rgba(47, 111, 94, 0.35)';
                ctx.lineWidth = 1.5;
                ctx.beginPath();
                ctx.arc(hoverPos.x, hoverPos.y, metrics.cell * 0.46, 0, Math.PI * 2);
                ctx.stroke();
                ctx.restore();
            }
        }

        function drawBoard() {
            var m = layout();
            ctx.clearRect(0, 0, m.width, m.height);
            drawWood();
            drawGrid();
            drawPalaces();
            drawRiver();
            drawPieces();
            drawSelection();
            drawLegalMoves();
            drawHighlights();
        }

        function draw(nextState, nextOpts) {
            if (!nextState || !nextState.board) {
                return;
            }
            state = nextState;
            opts = nextOpts || {};
            drawBoard();
        }

        function locate(clientX, clientY) {
            if (!metrics || !state) {
                return null;
            }
            var rect = canvas.getBoundingClientRect();
            var x = clientX - rect.left;
            var y = clientY - rect.top;
            var c = Math.round((x - metrics.padX) / metrics.cell);
            var r = Math.round((y - metrics.padY) / metrics.cell);
            if (r < 0 || c < 0 || r >= 10 || c >= 9) {
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
                if (state) {
                    draw(state, opts);
                }
            }
        };
    }

    window.QP.xiangqiRender = { create: create, colorOf: colorOf, typeOf: typeOf };
})();
