/* ------------------------------------------------------------------
   中国象棋页面控制器
   两段式走子：先点己方棋子取合法落点，再点落点走棋。
   前端不判断任何规则，合法落点、将军、胜负全部来自服务端。
   ------------------------------------------------------------------ */
(function () {
    'use strict';

    window.QP = window.QP || {};
    var QP = window.QP;

    var SOUND_KEY = 'qp.xiangqi.sound';
    var els = {};
    var boardView = null;
    var clockView = null;
    var state = null;
    var selected = null;
    var legal = [];
    var hover = null;
    var legalToken = 0;
    var busy = false;
    var active = false;
    var polling = false;
    var enterToken = 0;
    var timeoutAttempts = 0;
    var manualFlip = null;
    var notationLength = 0;
    var soundEnabled = readSoundPreference();
    var audioContext = null;
    var resultSignature = '';
    var mateNoticeKey = '';

    function el(id) {
        return document.getElementById(id);
    }

    function readSoundPreference() {
        try {
            return window.localStorage.getItem(SOUND_KEY) !== 'off';
        } catch (error) {
            return true;
        }
    }

    function saveSoundPreference() {
        try {
            window.localStorage.setItem(SOUND_KEY, soundEnabled ? 'on' : 'off');
        } catch (error) {
            // 隐私模式下无法持久化，不影响本次会话。
        }
    }

    function init() {
        els.game = el('xiangqi-view');
        els.canvas = el('xiangqi-board');
        els.overlay = el('xiangqi-overlay');
        els.overlayText = el('xiangqi-overlay-text');
        els.spinner = els.overlay ? els.overlay.querySelector('.spinner') : null;
        els.message = el('xiangqi-message');
        els.moves = el('xiangqi-moves');
        els.aiMillis = el('xiangqi-ai-millis');
        els.side = el('xiangqi-side');
        els.time = el('xiangqi-time');
        els.difficulty = el('xiangqi-difficulty');
        els.newBtn = el('xiangqi-new');
        els.undoBtn = el('xiangqi-undo');
        els.resignSoloBtn = el('xiangqi-resign-solo');
        els.createBtn = el('xiangqi-create');
        els.joinBtn = el('xiangqi-join');
        els.leaveBtn = el('xiangqi-leave');
        els.roomCode = el('xiangqi-room-code');
        els.roomDisplay = el('xiangqi-room-display');
        els.colorDisplay = el('xiangqi-color-display');
        els.youLabel = el('xiangqi-you-label');
        els.otherLabel = el('xiangqi-other-label');
        els.clockPanel = el('xiangqi-clock-panel');
        els.clock1 = el('xiangqi-clock-1');
        els.clock2 = el('xiangqi-clock-2');
        els.roomActions = el('xiangqi-room-actions');
        els.resignBtn = el('xiangqi-resign');
        els.drawBtn = el('xiangqi-draw');
        els.drawAnswer = el('xiangqi-draw-answer');
        els.drawAccept = el('xiangqi-draw-accept');
        els.drawDecline = el('xiangqi-draw-decline');
        els.rematchBtn = el('xiangqi-rematch');
        els.flipBtn = el('xiangqi-flip');
        els.soundBtn = el('xiangqi-sound');
        els.alert = el('xiangqi-alert');
        els.copyCodeBtn = el('xiangqi-copy-code');
        els.copyLinkBtn = el('xiangqi-copy-link');
        els.opponent = el('xiangqi-opponent');
        els.opponentDot = el('xiangqi-opponent-dot');
        els.opponentText = el('xiangqi-opponent-text');
        els.opponentSeen = el('xiangqi-opponent-seen');
        els.capturedRed = el('xiangqi-captured-red');
        els.capturedBlack = el('xiangqi-captured-black');
        els.material = el('xiangqi-material');
        els.notation = el('xiangqi-notation');
        els.notationEmpty = el('xiangqi-notation-empty');
        els.undoPvp = el('xiangqi-undo-pvp');
        els.undoAnswer = el('xiangqi-undo-answer');
        els.undoAccept = el('xiangqi-undo-accept');
        els.undoDecline = el('xiangqi-undo-decline');
        els.resultModal = el('xiangqi-result-modal');
        els.resultCard = el('xiangqi-result-card');
        els.resultTitle = el('xiangqi-result-title');
        els.resultSub = el('xiangqi-result-sub');
        els.resultDetail = el('xiangqi-result-detail');
        els.resultRematch = el('xiangqi-result-rematch');
        els.resultRematchHint = el('xiangqi-result-rematch-hint');
        els.resultClose = el('xiangqi-result-close');
        els.resultLobby = el('xiangqi-result-lobby');

        boardView = QP.xiangqiRender.create(els.canvas);
        clockView = QP.ui.createClock({
            firstEl: els.clock1,
            secondEl: els.clock2,
            rows: {
                1: els.clockPanel.querySelector('[data-clock-seat="1"]'),
                2: els.clockPanel.querySelector('[data-clock-seat="2"]')
            },
            onExpire: onClockExpire
        });

        bindEvents();
        updateSoundButton();
        updateFlipButton();
        if (window.ResizeObserver) {
            new ResizeObserver(function () {
                if (state) {
                    draw();
                }
            }).observe(els.canvas);
        } else {
            window.addEventListener('resize', draw);
        }
    }

    function bindEvents() {
        els.canvas.addEventListener('mousemove', function (event) {
            var next = canPlay() ? boardView.locate(event.clientX, event.clientY) : null;
            var changed = (!!next !== !!hover)
                || (next && hover && (next.r !== hover.r || next.c !== hover.c));
            if (changed) {
                hover = next;
                draw();
            }
        });
        els.canvas.addEventListener('mouseleave', function () {
            if (hover) {
                hover = null;
                draw();
            }
        });
        els.canvas.addEventListener('click', onBoardClick);

        document.addEventListener('pointerdown', function () {
            ensureAudio();
        }, { once: true });

        els.flipBtn.addEventListener('click', function () {
            manualFlip = !currentFlip();
            updateFlipButton();
            draw();
        });

        els.soundBtn.addEventListener('click', function () {
            soundEnabled = !soundEnabled;
            saveSoundPreference();
            updateSoundButton();
            if (soundEnabled) {
                ensureAudio();
                playMoveSound(null);
            }
        });

        els.newBtn.addEventListener('click', startSoloGame);

        els.undoBtn.addEventListener('click', function () {
            run('/api/xiangqi/undo', {}, '正在悔棋…').then(function (data) {
                if (data) {
                    clearSelection();
                    apply(data);
                }
            });
        });

        els.undoPvp.addEventListener('click', function () {
            run('/api/rooms/undo', {}, '正在发送悔棋申请…').then(function (data) {
                if (data) {
                    clearSelection();
                    apply(data);
                }
            });
        });
        els.undoAccept.addEventListener('click', function () {
            answerUndo('accept');
        });
        els.undoDecline.addEventListener('click', function () {
            answerUndo('decline');
        });
        els.resignSoloBtn.addEventListener('click', function () {
            if (!window.confirm('确定认输吗？')) {
                return;
            }
            run('/api/xiangqi/resign', {}, '正在认输…').then(function (data) {
                if (data) {
                    clearSelection();
                    apply(data);
                }
            });
        });

        els.difficulty.addEventListener('change', function () {
            var level = parseInt(els.difficulty.value, 10) || 2;
            run('/api/xiangqi/difficulty', { level: level }, '正在切换难度…').then(function (data) {
                if (data) {
                    apply(data);
                }
            });
        });

        document.querySelectorAll('[data-xiangqi-mode]').forEach(function (button) {
            button.addEventListener('click', function () {
                var mode = button.dataset.xiangqiMode;
                if (!state || mode === state.mode) {
                    return;
                }
                if (state.mode === 'pvp' && state.room
                        && !window.confirm('切换模式会离开当前房间，确定吗？')) {
                    return;
                }
                run('/api/xiangqi/mode', { mode: mode }, '正在切换模式…').then(function (data) {
                    if (data) {
                        clearSelection();
                        apply(data);
                    }
                });
            });
        });

        els.createBtn.addEventListener('click', createRoom);
        els.joinBtn.addEventListener('click', joinRoom);
        els.leaveBtn.addEventListener('click', function () {
            run('/api/rooms/leave', {}, '正在离开房间…').then(function (data) {
                if (data && data.game === 'xiangqi') {
                    clearSelection();
                    apply(data);
                }
            });
        });

        els.roomCode.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                joinRoom();
            }
        });

        els.resignBtn.addEventListener('click', function () {
            if (!window.confirm('确定认输吗？')) {
                return;
            }
            run('/api/rooms/resign', {}, '正在认输…').then(function (data) {
                if (data) {
                    clearSelection();
                    apply(data);
                }
            });
        });

        els.drawBtn.addEventListener('click', function () {
            run('/api/rooms/draw', { action: 'offer' }, '正在提和…').then(function (data) {
                if (data) {
                    apply(data);
                }
            });
        });
        els.drawAccept.addEventListener('click', function () {
            run('/api/rooms/draw', { action: 'accept' }, '正在结束对局…').then(function (data) {
                if (data) {
                    clearSelection();
                    apply(data);
                }
            });
        });
        els.drawDecline.addEventListener('click', function () {
            run('/api/rooms/draw', { action: 'decline' }, '正在回应…').then(function (data) {
                if (data) {
                    apply(data);
                }
            });
        });
        els.rematchBtn.addEventListener('click', function () {
            run('/api/rooms/rematch', {}, '正在准备下一盘…').then(function (data) {
                if (data) {
                    clearSelection();
                    apply(data);
                }
            });
        });

        els.copyCodeBtn.addEventListener('click', copyRoomCode);
        els.copyLinkBtn.addEventListener('click', copyInviteLink);

        els.resultRematch.addEventListener('click', requestResultRematch);
        els.resultClose.addEventListener('click', function () {
            closeResultModal(true);
        });
        els.resultLobby.addEventListener('click', function () {
            closeResultModal(false);
            QP.router.go('/lobby');
        });
        els.resultModal.addEventListener('click', function (event) {
            if (event.target === els.resultModal) {
                closeResultModal(true);
            }
        });
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && els.resultModal && !els.resultModal.hidden) {
                event.preventDefault();
                closeResultModal(true);
            }
        });
    }

    function startSoloGame() {
        var time = parseTime(els.time.value);
        return run('/api/xiangqi/new', {
            side: parseInt(els.side.value, 10) || 0,
            level: parseInt(els.difficulty.value, 10) || 2,
            minutes: time.minutes,
            increment: time.increment
        }, '正在开局…').then(function (data) {
            if (data) {
                clearSelection();
                apply(data);
            }
            return data;
        });
    }

    function answerUndo(action) {
        run('/api/rooms/undo/answer', { action: action },
                action === 'accept' ? '正在撤回上一手…' : '正在回应…').then(function (data) {
            if (data) {
                clearSelection();
                apply(data);
            }
        });
    }

    function activate() {
        active = true;
        var token = ++enterToken;
        var invite = invitedRoom();
        setBusy(true, '正在准备棋局…');
        QP.api.post('/api/session/game', { game: 'xiangqi' })
            .then(function (data) {
                if (token !== enterToken) {
                    return null;
                }
                if (!invite || (data.mode === 'pvp' && data.room === invite)) {
                    finishActivate(data);
                    return null;
                }
                return joinInvitation(invite, data, token);
            })
            .catch(function (error) {
                if (token !== enterToken) {
                    return;
                }
                setBusy(false);
                fail(error);
            });
    }

    function finishActivate(data) {
        setBusy(false);
        apply(data);
    }

    function joinInvitation(code, initial, token) {
        return QP.api.post('/api/xiangqi/mode', { mode: 'pvp' })
            .then(function () {
                return QP.api.post('/api/rooms/join', { room: code });
            })
            .then(function (data) {
                if (token !== enterToken) {
                    return;
                }
                setBusy(false);
                apply(data);
                QP.ui.toast('已加入房间 ' + code);
            })
            .catch(function (error) {
                if (token !== enterToken) {
                    return;
                }
                QP.ui.toast((error && error.message) || '邀请链接加入失败', 'error');
                return QP.api.get('/api/session/state').then(function (data) {
                    if (token !== enterToken) {
                        return;
                    }
                    setBusy(false);
                    apply(data && data.game === 'xiangqi' ? data : initial);
                }).catch(function () {
                    if (token !== enterToken) {
                        return;
                    }
                    setBusy(false);
                    apply(initial);
                });
            });
    }

    function invitedRoom() {
        try {
            return String((QP.router.query() || {}).room || '').trim().toUpperCase();
        } catch (error) {
            return '';
        }
    }

    function deactivate() {
        active = false;
        enterToken++;
        clearSelection();
        hover = null;
        closeResultModal(false);
        resultSignature = '';
        mateNoticeKey = '';
        document.title = '中国象棋 · 棋类对战平台';
    }

    function run(url, payload, label) {
        setBusy(true, label || '处理中…');
        return QP.api.post(url, payload || {}).then(function (data) {
            setBusy(false);
            return data;
        }).catch(function (error) {
            setBusy(false);
            fail(error);
            return null;
        });
    }

    function fail(error) {
        var text = (error && error.message) || '操作失败';
        QP.ui.toast(text, 'error');
        if (state) {
            QP.ui.setText(els.message, text);
            QP.ui.setTone(els.message, 'error');
        }
    }

    function identityOf(data) {
        if (!data) {
            return '';
        }
        var color = data.mode === 'pvp'
            ? (data.mySeat || 0)
            : (data.playerColor || data.myColor || 0);
        return [data.mode, data.room || '', color].join('|');
    }

    function apply(data, silent) {
        if (!data || data.game !== 'xiangqi') {
            return;
        }
        var prevStatus = state ? state.status : '';
        var prevIdentity = identityOf(state);
        var firstLoad = !state;
        state = data;
        if (!silent) {
            hover = null;
        }
        if (prevIdentity !== identityOf(state)) {
            // 换了房间或换了阵营时回到默认视角：黑方自动翻转。
            manualFlip = null;
            resultSignature = '';
            mateNoticeKey = '';
        }

        var pvp = state.mode === 'pvp';
        var inRoom = pvp && !!state.room;
        els.game.classList.toggle('mode-pvp', pvp);
        els.game.classList.toggle('mode-solo', !pvp);
        els.game.classList.toggle('has-room', inRoom);

        document.querySelectorAll('[data-xiangqi-mode]').forEach(function (button) {
            button.classList.toggle('is-active', button.dataset.xiangqiMode === state.mode);
        });

        if (state.difficulty) {
            els.difficulty.value = String(state.difficulty);
        }
        QP.ui.setText(els.moves, state.moves || 0);
        QP.ui.setText(els.message, state.message || '');
        QP.ui.setTone(els.message, messageTone());

        if (pvp) {
            QP.ui.setText(els.roomDisplay, state.room || '—');
            QP.ui.setText(els.colorDisplay, sideName(myColor()));
        } else {
            QP.ui.setText(els.aiMillis, (state.aiMillis || 0) + ' ms');
        }

        updateLegend(pvp);
        updateOpponentStatus(pvp && inRoom);
        renderCaptured();
        renderNotation();
        updateFlipButton();

        els.clockPanel.hidden = !state.clock;
        clockView.set(state.clock || null);
        if (state.status !== 'playing') {
            timeoutAttempts = 0;
        }

        syncControls();
        syncOverlay();
        draw();
        updateAlert();
        syncResultModal();
        updateTitle();
        notifyTransitions(prevStatus, firstLoad);
    }

    function messageTone() {
        if (!state) {
            return 'info';
        }
        if (state.status === 'draw') {
            return 'info';
        }
        var winner = state.status === 'red_win' ? 1 : (state.status === 'black_win' ? 2 : 0);
        if (!winner) {
            return 'info';
        }
        return myColor() === winner ? 'win' : 'lose';
    }

    function sideName(seat) {
        if (seat === 1) {
            return '红方';
        }
        if (seat === 2) {
            return '黑方';
        }
        return '—';
    }

    function paintDot(dot, seat) {
        if (!dot) {
            return;
        }
        dot.className = 'side-dot '
            + (seat === 1 ? 'side-dot--red' : (seat === 2 ? 'side-dot--black' : 'side-dot--none'));
    }

    function updateLegend(pvp) {
        var mine = myColor();
        var other = mine ? 3 - mine : 0;
        var youDot = els.youLabel ? els.youLabel.parentNode.querySelector('.side-dot') : null;
        var otherDot = els.otherLabel ? els.otherLabel.parentNode.querySelector('.side-dot') : null;
        QP.ui.setText(els.youLabel, mine ? '你（' + sideName(mine) + '）' : '你');
        if (!pvp) {
            paintDot(youDot, mine);
            paintDot(otherDot, other);
            QP.ui.setText(els.otherLabel, other ? '电脑（' + sideName(other) + '）' : '电脑');
            return;
        }
        paintDot(youDot, mine);
        paintDot(otherDot, other);
        QP.ui.setText(els.otherLabel, other ? '对手（' + sideName(other) + '）' : '对手');
    }

    function updateOpponentStatus(show) {
        if (!els.opponent) {
            return;
        }
        els.opponent.hidden = !show;
        if (!show) {
            return;
        }
        var present = myColor() === 1 ? !!state.secondPresent : !!state.firstPresent;
        var online = !!state.opponentOnline;
        var cls = 'opponent-status pvp-only';
        if (!present) {
            cls += ' is-waiting';
            QP.ui.setText(els.opponentText, '等待对手加入');
            QP.ui.setText(els.opponentSeen, '把房间码或邀请链接发给朋友');
        } else if (online) {
            cls += ' is-online';
            QP.ui.setText(els.opponentText, '对手在线');
            QP.ui.setText(els.opponentSeen, '');
        } else {
            cls += ' is-offline';
            QP.ui.setText(els.opponentText, '对手暂时离线');
            QP.ui.setText(els.opponentSeen, lastSeenText(state.opponentLastSeenMs));
        }
        els.opponent.className = cls;
    }

    function lastSeenText(ms) {
        if (ms === null || ms === undefined || ms < 0) {
            return '';
        }
        if (ms < 15000) {
            return '刚刚还在';
        }
        if (ms < 60000) {
            return Math.max(1, Math.round(ms / 1000)) + ' 秒前在线';
        }
        if (ms < 3600000) {
            return Math.round(ms / 60000) + ' 分钟前在线';
        }
        return '很久没出现了';
    }

    function reasonText(reason) {
        switch (reason) {
            case 'checkmate': return '将死';
            case 'stalemate': return '困毙';
            case 'natural_limit': return '60 回合自然限着';
            case 'repetition': return '重复局面裁决';
            case 'insufficient': return '双方均无取胜可能';
            case 'timeout': return '超时';
            case 'resign': return '认输';
            case 'draw_agreed': return '双方同意和棋';
            case 'opponent_left': return '对手离开';
            default: return '';
        }
    }

    function resultText() {
        if (!state) {
            return '';
        }
        var reason = reasonText(state.endReason);
        var prefix = reason ? reason + '，' : '';
        if (state.status === 'draw') {
            return prefix + '本局和棋';
        }
        var winner = state.status === 'red_win' ? 1 : 2;
        if (state.mode === 'pvp') {
            return prefix + sideName(winner) + '胜';
        }
        return prefix + (winner === myColor() ? '你赢了！' : '你输了');
    }

    function resultSignatureOf(data) {
        return [data.mode, data.room || '', data.moves || 0, data.status, data.endReason || ''].join('|');
    }

    /**
     * 终局弹窗按“对局签名”去重：轮询到同一盘结束状态只弹一次，
     * 用户点“查看棋盘”后也不会在下一轮轮询时重新出现。
     */
    function syncResultModal() {
        if (!els.resultModal) {
            return;
        }
        if (!state || state.status === 'playing') {
            resultSignature = '';
            closeResultModal(false);
            return;
        }
        var signature = resultSignatureOf(state);
        if (resultSignature === signature) {
            if (!els.resultModal.hidden) {
                updateResultActions();
            }
            return;
        }
        resultSignature = signature;
        renderResultModal();
        els.resultModal.hidden = false;
        updateResultActions();
        focusWithoutScroll(els.resultClose);
    }

    function renderResultModal() {
        var draw = state.status === 'draw';
        var winner = state.status === 'red_win' ? 1 : (state.status === 'black_win' ? 2 : 0);
        var mine = myColor();
        var won = !draw && winner !== 0 && winner === mine;
        var tone = draw ? 'draw' : (won ? 'win' : 'lose');
        var reason = reasonText(state.endReason);

        if (els.resultCard) {
            els.resultCard.className = 'result-card result-card--' + tone;
        }
        QP.ui.setText(els.resultTitle, draw ? '和棋' : (won ? '🏆 胜利！' : (mine ? '本局结束' : '对局结束')));

        var sub = reason;
        if (!draw && winner) {
            sub = (sub ? sub + ' · ' : '') + sideName(winner) + '胜';
        }
        if (!sub) {
            sub = draw ? '双方和棋' : '本局结束';
        }
        QP.ui.setText(els.resultSub, sub);
        QP.ui.setText(els.resultDetail, '共 ' + (state.moves || 0) + ' 手');
    }

    function updateResultActions() {
        if (!els.resultRematch || !state) {
            return;
        }
        var pvp = state.mode === 'pvp';
        var mine = myColor();
        var votes = state.rematchVotes || [];
        var voted = !!(pvp && mine && votes[mine - 1]);
        var count = 0;
        for (var i = 0; i < votes.length; i++) {
            if (votes[i]) {
                count++;
            }
        }

        els.resultRematch.hidden = pvp && !state.room;
        QP.ui.setText(els.resultRematch, voted ? '等待对手确认' : '再来一局');
        QP.ui.setDisabled(els.resultRematch, busy || voted);

        if (pvp && count > 0) {
            els.resultRematchHint.hidden = false;
            QP.ui.setText(els.resultRematchHint, voted
                ? '等待对手确认（' + count + '/2）'
                : '对手已准备（' + count + '/2），点「再来一局」即可开局');
        } else {
            els.resultRematchHint.hidden = true;
        }
    }

    function requestResultRematch() {
        if (!state || busy) {
            return;
        }
        if (state.mode === 'pvp') {
            run('/api/rooms/rematch', {}, '正在准备下一盘…').then(function (data) {
                if (data) {
                    clearSelection();
                    apply(data);
                }
            });
            return;
        }
        startSoloGame();
    }

    function closeResultModal(restoreFocus) {
        if (!els.resultModal || els.resultModal.hidden) {
            return;
        }
        els.resultModal.hidden = true;
        if (restoreFocus) {
            focusWithoutScroll(els.flipBtn);
        }
    }

    function focusWithoutScroll(target) {
        if (!target || !target.focus) {
            return;
        }
        try {
            target.focus({ preventScroll: true });
        } catch (error) {
            target.focus();
        }
    }

    function updateAlert() {
        if (!els.alert) {
            return;
        }
        var text = '';
        var toneName = 'info';
        if (!state) {
            els.alert.hidden = true;
            return;
        }
        if (state.status !== 'playing') {
            text = resultText();
            toneName = messageTone();
        } else if (state.mateInOne && state.mateFor) {
            text = state.mateFor === myColor()
                ? '你有一手将死机会'
                : '警告：' + sideName(state.mateFor) + '有一手将死机会';
            toneName = 'mate';
        } else if (state.check) {
            text = '将军！';
            toneName = 'check';
        } else if (state.undoOffer) {
            text = state.undoOffer === myColor() ? '已申请悔棋，等待对方回应' : '对方申请悔棋，请选择接受或拒绝';
            toneName = 'info';
        } else if (state.drawOffer) {
            text = state.drawOffer === myColor() ? '已向对手提和，等待回应' : '对方提议和棋，请选择接受或拒绝';
            toneName = 'info';
        }
        els.alert.hidden = !text;
        if (text) {
            QP.ui.setText(els.alert, text);
            els.alert.dataset.tone = toneName;
        } else if (els.alert.dataset.tone) {
            delete els.alert.dataset.tone;
        }
    }    var PIECE_LABELS = {
        1: { 1: '帅', 2: '仕', 3: '相', 4: '马', 5: '车', 6: '炮', 7: '兵' },
        2: { 1: '将', 2: '士', 3: '象', 4: '马', 5: '车', 6: '炮', 7: '卒' }
    };

    function pieceName(piece) {
        var color = Math.floor(piece / 10);
        var type = piece % 10;
        var set = PIECE_LABELS[color];
        return (set && set[type]) || '?';
    }

    function renderCapturedRow(target, list) {
        if (!target) {
            return;
        }
        target.innerHTML = '';
        var pieces = list || [];
        if (!pieces.length) {
            var empty = document.createElement('span');
            empty.className = 'captured-row__empty';
            empty.textContent = '—';
            target.appendChild(empty);
            return;
        }
        pieces.forEach(function (piece) {
            var span = document.createElement('span');
            span.className = 'captured-piece ' + (Math.floor(piece / 10) === 1 ? 'is-red' : 'is-black');
            span.textContent = pieceName(piece);
            target.appendChild(span);
        });
    }

    function renderCaptured() {
        if (!state) {
            return;
        }
        renderCapturedRow(els.capturedRed, state.capturedRed);
        renderCapturedRow(els.capturedBlack, state.capturedBlack);
        var diff = state.materialDiff || 0;
        var text = '子力差：均势';
        if (diff > 0) {
            text = '子力差：红方 +' + diff;
        } else if (diff < 0) {
            text = '子力差：黑方 +' + (-diff);
        }
        QP.ui.setText(els.material, text);
    }

    function renderNotation() {
        if (!els.notation || !state) {
            return;
        }
        var history = state.history || [];
        els.notation.innerHTML = '';
        els.notationEmpty.hidden = history.length > 0;
        if (!history.length) {
            return;
        }
        var lastIndex = history.length - 1;
        var rows = Math.ceil(history.length / 2);
        for (var i = 0; i < rows; i++) {
            var li = document.createElement('li');
            li.className = 'notation-row';
            var no = document.createElement('span');
            no.className = 'notation-row__no';
            no.textContent = (i + 1) + '.';
            li.appendChild(no);
            for (var k = 0; k < 2; k++) {
                var index = i * 2 + k;
                var move = document.createElement('span');
                move.className = 'notation-row__move';
                move.textContent = history[index] || '';
                if (index === lastIndex) {
                    move.classList.add('is-latest');
                }
                li.appendChild(move);
            }
            els.notation.appendChild(li);
        }
        els.notation.scrollTop = els.notation.scrollHeight;
    }

    function syncControls() {
        if (!state) {
            return;
        }
        var pvp = state.mode === 'pvp';
        var inRoom = pvp && !!state.room;
        var playing = state.status === 'playing';
        var phase = state.phase || (playing ? 'playing' : 'finished');
        var mine = myColor();

        var canUndo = playing && (state.moves || 0) > 0;
        if (pvp) {
            canUndo = canUndo && inRoom && !!state.lastMove
                && state.lastMove.color === mine
                && state.undoOffer === 0;
        }

        QP.ui.setDisabled(els.undoBtn, busy || !canUndo);
        QP.ui.setDisabled(els.undoPvp, busy || !canUndo);
        QP.ui.setDisabled(els.newBtn, busy);
        QP.ui.setDisabled(els.side, busy);
        QP.ui.setDisabled(els.time, busy);
        QP.ui.setDisabled(els.difficulty, busy);
        QP.ui.setDisabled(els.createBtn, busy || inRoom);
        QP.ui.setDisabled(els.joinBtn, busy || inRoom);
        QP.ui.setDisabled(els.leaveBtn, busy || !inRoom);
        QP.ui.setDisabled(els.roomCode, busy || inRoom);

        els.roomActions.hidden = !inRoom;
        QP.ui.setDisabled(els.resignBtn, !playing || !inRoom);
        QP.ui.setDisabled(els.resignSoloBtn, !playing || pvp);
        QP.ui.setDisabled(els.drawBtn, !playing || !inRoom || state.drawOffer === mine);
        els.drawAnswer.hidden = !(playing && inRoom && state.drawOffer && state.drawOffer !== mine);
        els.undoAnswer.hidden = !(playing && inRoom && state.undoOffer && state.undoOffer !== mine);
        els.rematchBtn.hidden = !inRoom || phase !== 'finished';
        els.rematchBtn.textContent = (state.rematchVotes && mine && state.rematchVotes[mine - 1])
            ? '等待对手…'
            : '再来一盘';
        if (els.undoPvp) {
            QP.ui.setText(els.undoPvp, state.undoOffer === mine ? '已申请悔棋…' : '申请悔棋');
        }
    }

    function syncOverlay() {
        if (!els.overlay) {
            return;
        }
        var show = false;
        var text = '';
        if (busy) {
            show = true;
            text = els.overlayText.textContent;
            if (els.spinner) {
                els.spinner.hidden = false;
            }
        } else if (state && state.mode === 'pvp' && state.phase === 'waiting') {
            show = true;
            text = state.room ? ('等待对手加入… 房间码 ' + state.room) : '等待对手加入…';
            if (els.spinner) {
                els.spinner.hidden = false;
            }
        } else if (state && state.mode === 'pvp' && state.phase === 'lobby') {
            show = true;
            text = '创建或加入一个房间后开始联机对局';
            if (els.spinner) {
                els.spinner.hidden = true;
            }
        } else if (state && state.mode === 'solo' && state.phase === 'ready') {
            show = true;
            text = '选择执红或执黑，点「新对局」开始';
            if (els.spinner) {
                els.spinner.hidden = true;
            }
        }
        els.overlay.hidden = !show;
        if (show) {
            QP.ui.setText(els.overlayText, text);
        }
    }

    function setBusy(value, text) {
        busy = value;
        if (value && text) {
            QP.ui.setText(els.overlayText, text);
        }
        syncControls();
        syncOverlay();
        draw();
    }

    function myColor() {
        if (!state) {
            return 0;
        }
        if (state.mode === 'pvp') {
            return state.mySeat || 0;
        }
        return state.playerColor || state.myColor || 0;
    }

    function currentFlip() {
        return manualFlip === null ? myColor() === 2 : !!manualFlip;
    }

    function updateFlipButton() {
        if (!els.flipBtn) {
            return;
        }
        QP.ui.setText(els.flipBtn, currentFlip() ? '视角：黑方在下' : '视角：红方在下');
        els.flipBtn.classList.toggle('is-active', manualFlip !== null);
    }

    function updateSoundButton() {
        if (!els.soundBtn) {
            return;
        }
        QP.ui.setText(els.soundBtn, soundEnabled ? '音效：开' : '音效：关');
        els.soundBtn.classList.toggle('is-active', soundEnabled);
    }

    function updateTitle() {
        if (!active || !state || state.mode !== 'pvp') {
            return;
        }
        if (state.phase === 'waiting') {
            document.title = '等待对手加入 · 中国象棋';
        } else if (state.phase === 'finished') {
            document.title = '对局结束 · 中国象棋';
        } else if (state.status === 'playing') {
            document.title = (state.turn === myColor() ? '轮到你走棋 · 中国象棋' : '等待对手 · 中国象棋');
        }
    }

    function canPlay() {
        if (!state || busy || state.status !== 'playing') {
            return false;
        }
        if (!myColor() || state.turn !== myColor()) {
            return false;
        }
        if (state.mode === 'pvp') {
            return state.phase === 'playing';
        }
        return state.phase === 'playing';
    }

    function draw() {
        if (!state || !state.board) {
            return;
        }
        boardView.draw(state, {
            flip: currentFlip(),
            hover: hover,
            canPlay: canPlay(),
            selected: selected,
            legal: legal
        });
    }

    function clearSelection() {
        selected = null;
        legal = [];
        legalToken++;
    }

    function isLegalTarget(r, c) {
        for (var i = 0; i < legal.length; i++) {
            if (legal[i].r === r && legal[i].c === c) {
                return true;
            }
        }
        return false;
    }

    function onBoardClick(event) {
        if (!state || busy) {
            return;
        }
        var hit = boardView.locate(event.clientX, event.clientY);
        if (!hit) {
            return;
        }
        if (state.mode === 'pvp') {
            if (state.phase === 'lobby') {
                QP.ui.toast('先创建或加入一个房间');
                return;
            }
            if (state.phase === 'waiting') {
                QP.ui.toast('等待对手加入后再走棋');
                return;
            }
        } else if (state.phase !== 'playing') {
            QP.ui.toast('先点「新对局」开始');
            return;
        }
        if (state.status !== 'playing') {
            QP.ui.toast('本局已经结束，点「再来一盘」继续');
            return;
        }
        if (state.undoOffer && state.undoOffer !== myColor()) {
            QP.ui.toast('请先处理对方的悔棋申请');
            return;
        }
        if (!myColor() || state.turn !== myColor()) {
            QP.ui.toast('还没轮到你走棋');
            return;
        }

        var piece = state.board[hit.r][hit.c];
        var own = piece !== 0 && Math.floor(piece / 10) === myColor();
        if (selected && isLegalTarget(hit.r, hit.c)) {
            sendMove(selected.r, selected.c, hit.r, hit.c);
            return;
        }
        if (own) {
            selectPiece(hit.r, hit.c);
            return;
        }
        if (selected) {
            clearSelection();
            draw();
        }
    }

    function normalizeMoves(moves) {
        var list = moves || [];
        var result = [];
        for (var i = 0; i < list.length; i++) {
            var item = list[i];
            if (Array.isArray(item)) {
                result.push({ r: item[0], c: item[1] });
            } else if (item && typeof item.r === 'number' && typeof item.c === 'number') {
                result.push({ r: item.r, c: item.c });
            }
        }
        return result;
    }

    function selectPiece(r, c) {
        if (selected && selected.r === r && selected.c === c) {
            clearSelection();
            draw();
            return;
        }
        selected = { r: r, c: c };
        legal = [];
        draw();
        var token = ++legalToken;
        var url = state.mode === 'pvp' ? '/api/rooms/legal' : '/api/xiangqi/legal';
        QP.api.post(url, { r: r, c: c }).then(function (data) {
            if (token !== legalToken) {
                return;
            }
            legal = normalizeMoves(data && data.moves);
            draw();
        }).catch(function (error) {
            if (token !== legalToken) {
                return;
            }
            legal = [];
            selected = null;
            draw();
            QP.ui.toast((error && error.message) || '无法获取合法落点', 'error');
        });
    }

    function sendMove(fr, fc, tr, tc) {
        var url = state.mode === 'pvp' ? '/api/rooms/move' : '/api/xiangqi/move';
        setBusy(true, state.mode === 'pvp' ? '正在走棋…' : '电脑思考中…');
        QP.api.post(url, { fr: fr, fc: fc, tr: tr, tc: tc }).then(function (data) {
            setBusy(false);
            clearSelection();
            apply(data);
        }).catch(function (error) {
            setBusy(false);
            clearSelection();
            draw();
            fail(error);
        });
    }

    function createRoom() {
        QP.ui.roomModal.open('xiangqi').then(function (choice) {
            if (!choice) {
                return;
            }
            run('/api/rooms/create', {
                game: 'xiangqi',
                seat: choice.seat,
                minutes: choice.minutes,
                increment: choice.increment
            }, '正在创建房间…').then(function (data) {
                if (data) {
                    clearSelection();
                    apply(data);
                }
            });
        });
    }

    function joinRoom() {
        var code = (els.roomCode.value || '').trim().toUpperCase();
        if (code.length !== 6) {
            QP.ui.toast('请输入 6 位房间码', 'error');
            els.roomCode.focus();
            return;
        }
        run('/api/rooms/join', { room: code }, '正在加入房间…').then(function (data) {
            if (data) {
                els.roomCode.value = '';
                clearSelection();
                apply(data);
            }
        });
    }

    function copyRoomCode() {
        if (!state || !state.room) {
            QP.ui.toast('当前还没有房间码', 'error');
            return;
        }
        QP.ui.copyText(state.room).then(function () {
            QP.ui.toast('房间码已复制：' + state.room);
        }, function (error) {
            QP.ui.toast((error && error.message) || '复制失败', 'error');
        });
    }

    function copyInviteLink() {
        if (!state || !state.room) {
            QP.ui.toast('当前还没有房间', 'error');
            return;
        }
        var link = location.origin + location.pathname
            + '#/xiangqi?room=' + encodeURIComponent(state.room);
        QP.ui.copyText(link).then(function () {
            QP.ui.toast('邀请链接已复制，发给朋友即可直接入座');
        }, function (error) {
            QP.ui.toast((error && error.message) || '复制失败', 'error');
        });
    }

    function parseTime(value) {
        var parts = String(value || '0:0').split(':');
        return {
            minutes: parseInt(parts[0], 10) || 0,
            increment: parseInt(parts[1], 10) || 0
        };
    }

    /** 只在棋谱或胜负真正变化时出声，轮询到相同状态不会重复播放。 */
    function notifyTransitions(prevStatus, firstLoad) {
        var history = state.history || [];
        var grew = !firstLoad && history.length > notationLength;
        var mateKey = state.status === 'playing' && state.mateInOne && state.mateFor
            ? String(state.mateFor)
            : '';
        notationLength = history.length;
        if (firstLoad) {
            mateNoticeKey = mateKey;
            return;
        }
        if (grew) {
            playMoveSound(state.lastMove);
        }
        if (state.status !== prevStatus && state.status !== 'playing') {
            if (state.status === 'draw') {
                playTone('draw', 0.3);
            } else {
                var winner = state.status === 'red_win' ? 1 : 2;
                playTone(myColor() === winner ? 'win' : 'lose', 0.32);
            }
        } else if (grew && state.check) {
            playTone('check', 0.26);
        }
        if (mateKey && mateKey !== mateNoticeKey) {
            playTone(state.mateFor === myColor() ? 'mate' : 'mate-warning', 0.27);
        }
        mateNoticeKey = mateKey;
    }

    function ensureAudio() {
        if (!soundEnabled) {
            return null;
        }
        if (!audioContext) {
            var Ctor = window.AudioContext || window.webkitAudioContext;
            if (!Ctor) {
                return null;
            }
            try {
                audioContext = new Ctor();
            } catch (error) {
                audioContext = null;
            }
        }
        if (audioContext && audioContext.state === 'suspended') {
            var resumed = audioContext.resume();
            if (resumed && resumed.catch) {
                resumed.catch(function () {});
            }
        }
        return audioContext;
    }

    function beep(freq, duration, volume, wave) {
        var ctx = ensureAudio();
        if (!ctx) {
            return;
        }
        var osc = ctx.createOscillator();
        var gain = ctx.createGain();
        var now = ctx.currentTime;
        osc.type = wave || 'sine';
        osc.frequency.value = freq;
        gain.gain.setValueAtTime(0.0001, now);
        gain.gain.exponentialRampToValueAtTime(volume || 0.2, now + 0.012);
        gain.gain.exponentialRampToValueAtTime(0.0001, now + duration);
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start(now);
        osc.stop(now + duration + 0.03);
    }

    function playTone(kind, volume) {
        if (!soundEnabled) {
            return;
        }
        if (kind === 'check') {
            beep(1046, 0.1, volume, 'square');
            setTimeout(function () {
                beep(880, 0.12, volume, 'square');
            }, 110);
        } else if (kind === 'win') {
            beep(523, 0.12, volume, 'sine');
            setTimeout(function () { beep(659, 0.12, volume, 'sine'); }, 130);
            setTimeout(function () { beep(784, 0.2, volume, 'sine'); }, 260);
        } else if (kind === 'lose') {
            beep(392, 0.16, volume, 'sine');
            setTimeout(function () { beep(294, 0.24, volume, 'sine'); }, 170);
        } else if (kind === 'mate') {
            beep(784, 0.09, volume, 'triangle');
            setTimeout(function () { beep(1046, 0.09, volume, 'triangle'); }, 95);
            setTimeout(function () { beep(1318, 0.18, volume, 'triangle'); }, 190);
        } else if (kind === 'mate-warning') {
            beep(660, 0.12, volume, 'square');
            setTimeout(function () { beep(494, 0.18, volume, 'square'); }, 130);
        } else {
            beep(440, 0.18, volume, 'sine');
        }
    }

    function playMoveSound(move) {
        if (!soundEnabled) {
            return;
        }
        if (move && move.captured) {
            beep(150, 0.18, 0.22, 'triangle');
        } else {
            beep(430, 0.08, 0.14, 'triangle');
        }
    }

    function onClockExpire() {
        if (active) {
            retryTimeout();
        }
    }

    function retryTimeout() {
        if (timeoutAttempts >= 3) {
            return;
        }
        timeoutAttempts++;
        setTimeout(function () {
            if (!active) {
                return;
            }
            var url = state && state.mode === 'pvp' ? '/api/rooms/state' : '/api/xiangqi/state';
            QP.api.get(url).then(function (data) {
                if (!active || !data || data.game !== 'xiangqi') {
                    return;
                }
                apply(data, true);
                if (data.status === 'playing' && timeoutAttempts < 3) {
                    retryTimeout();
                } else if (data.status !== 'playing') {
                    timeoutAttempts = 0;
                }
            }).catch(function () {
                // 稍后由轮询兜底
            });
        }, 500);
    }

    function poll() {
        if (!active || busy || polling || !state) {
            return;
        }
        if (state.mode !== 'pvp' || !state.room) {
            return;
        }
        polling = true;
        QP.api.get('/api/rooms/state').then(function (data) {
            if (!active || !data || data.game !== 'xiangqi') {
                return;
            }
            apply(data, true);
        }).catch(function () {
            // 下一次自动重试
        }).then(function () {
            polling = false;
        });
    }

    function tick() {
        if (active && state) {
            clockView.tick();
        }
    }

    window.QP.xiangqiView = {
        init: init,
        activate: activate,
        deactivate: deactivate,
        poll: poll,
        tick: tick
    };
})();