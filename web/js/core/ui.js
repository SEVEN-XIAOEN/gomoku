/* ------------------------------------------------------------------
   通用界面工具：DOM 取值、提示条、计时显示、创建房间弹窗。
   ------------------------------------------------------------------ */
(function () {
    'use strict';

    window.QP = window.QP || {};
    var QP = window.QP;

    function byId(id) {
        return document.getElementById(id);
    }

    function pad2(value) {
        return value < 10 ? '0' + value : String(value);
    }

    /** 毫秒 -> 棋钟文本。20 秒以内显示到 0.1 秒，方便读秒。 */
    function formatClock(ms) {
        if (ms === null || ms === undefined) {
            return '--:--';
        }
        var left = Math.max(0, ms);
        if (left < 20000) {
            var seconds = left / 1000;
            return '0:' + (seconds < 10 ? '0' : '') + seconds.toFixed(1);
        }
        var whole = Math.floor(left / 1000);
        return Math.floor(whole / 60) + ':' + pad2(whole % 60);
    }

    function setText(el, text) {
        if (el && el.textContent !== String(text)) {
            el.textContent = String(text);
        }
    }

    function setTone(el, tone) {
        if (!el) {
            return;
        }
        if (tone && tone !== 'info') {
            el.dataset.tone = tone;
        } else {
            delete el.dataset.tone;
        }
    }

    function setDisabled(el, disabled) {
        if (el) {
            el.disabled = !!disabled;
        }
    }

    var toastTimer = null;
    var toastSeq = 0;

    function toast(text, tone) {
        var el = byId('toast');
        if (!el) {
            return;
        }
        var seq = ++toastSeq;
        el.textContent = text;
        el.dataset.tone = tone || 'info';
        el.hidden = false;
        requestAnimationFrame(function () {
            el.classList.add('is-visible');
        });
        clearTimeout(toastTimer);
        toastTimer = setTimeout(function () {
            if (seq !== toastSeq) {
                return;
            }
            el.classList.remove('is-visible');
            setTimeout(function () {
                if (seq === toastSeq) {
                    el.hidden = true;
                }
            }, 220);
        }, 2800);
    }

    /** 复制文本：优先 Clipboard API，HTTP/旧浏览器回退到隐藏 textarea。 */
    function copyText(text) {
        function fallback() {
            return new Promise(function (resolve, reject) {
                var area = document.createElement('textarea');
                area.value = text;
                area.setAttribute('readonly', '');
                area.style.position = 'fixed';
                area.style.left = '-9999px';
                area.style.top = '0';
                document.body.appendChild(area);
                area.select();
                area.setSelectionRange(0, area.value.length);
                var ok = false;
                try {
                    ok = document.execCommand('copy');
                } catch (error) {
                    ok = false;
                }
                document.body.removeChild(area);
                if (ok) {
                    resolve();
                } else {
                    reject(new Error('当前浏览器不支持自动复制'));
                }
            });
        }
        if (navigator.clipboard && navigator.clipboard.writeText) {
            return navigator.clipboard.writeText(text).catch(fallback);
        }
        return fallback();
    }

    function netStatus(tone, text) {
        var el = byId('net-status');
        if (!el) {
            return;
        }
        el.dataset.tone = tone;
        el.textContent = text;
    }

    /**
     * 棋钟显示。快照来自服务端，本地按 performance.now 插值；
     * 每次轮询到新状态都会重新校准，避免长时间累积误差。
     */
    function createClock(options) {
        var snapshot = null;
        var syncedAt = 0;
        var expired = false;

        function remaining(seat) {
            if (!snapshot) {
                return null;
            }
            var base = seat === 1 ? snapshot.firstMs : snapshot.secondMs;
            if (!snapshot.running || snapshot.turn !== seat) {
                return Math.max(0, base);
            }
            return Math.max(0, base - (performance.now() - syncedAt));
        }

        function markActive(seat) {
            if (!options.rows) {
                return;
            }
            [1, 2].forEach(function (key) {
                var row = options.rows[key];
                if (row) {
                    row.classList.toggle('is-active',
                        !!snapshot && snapshot.running && snapshot.turn === seat && key === seat);
                }
            });
        }

        function tick() {
            if (!snapshot) {
                setText(options.firstEl, options.unlimitedText || '∞');
                setText(options.secondEl, options.unlimitedText || '∞');
                markActive(0);
                return;
            }
            var first = remaining(1);
            var second = remaining(2);
            setText(options.firstEl, formatClock(first));
            setText(options.secondEl, formatClock(second));
            markActive(snapshot.turn);

            if (!expired && snapshot.running && (first <= 0 || second <= 0)) {
                expired = true;
                if (options.onExpire) {
                    options.onExpire();
                }
            }
        }

        return {
            set: function (clock) {
                snapshot = clock || null;
                syncedAt = performance.now();
                expired = false;
                tick();
            },
            tick: tick,
            hasClock: function () {
                return !!snapshot;
            }
        };
    }

    /* ---------------------------- 创建房间弹窗 ---------------------------- */

    var modal = null;
    var modalResolver = null;

    function initModal() {
        modal = byId('room-modal');
        if (!modal) {
            return;
        }
        var seat = byId('room-modal-seat');
        var ok = byId('room-modal-ok');
        var cancel = byId('room-modal-cancel');

        ok.addEventListener('click', function () {
            var time = byId('room-modal-time');
            var parts = (time.value || '5:3').split(':');
            closeModal({
                seat: parseInt(seat.value, 10) || 0,
                minutes: parseInt(parts[0], 10) || 0,
                increment: parseInt(parts[1], 10) || 0
            });
        });
        cancel.addEventListener('click', function () {
            closeModal(null);
        });
        modal.addEventListener('click', function (event) {
            if (event.target === modal) {
                closeModal(null);
            }
        });
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && modal && !modal.hidden) {
                closeModal(null);
            }
        });
    }

    function closeModal(value) {
        if (!modal) {
            return;
        }
        modal.hidden = true;
        if (modalResolver) {
            var resolve = modalResolver;
            modalResolver = null;
            resolve(value);
        }
    }

    function openRoomModal(game) {
        if (!modal) {
            initModal();
        }
        if (!modal) {
            return Promise.resolve(null);
        }
        if (modalResolver) {
            closeModal(null);
        }

        var seat = byId('room-modal-seat');
        var title = byId('room-modal-title');
        var sub = byId('room-modal-sub');
        var choices = game === 'xiangqi'
            ? [[1, '红方（先行）'], [2, '黑方（后行）'], [0, '随机']]
            : [[1, '黑棋（先手）'], [2, '白棋（后手）'], [0, '随机']];

        seat.innerHTML = '';
        choices.forEach(function (choice) {
            var option = document.createElement('option');
            option.value = String(choice[0]);
            option.textContent = choice[1];
            seat.appendChild(option);
        });
        seat.value = '1';

        title.textContent = game === 'xiangqi' ? '创建中国象棋房间' : '创建五子棋房间';
        sub.textContent = game === 'xiangqi'
            ? '九路十行，红先黑后。创建后把 6 位房间码发给朋友。'
            : '创建后把 6 位房间码发给朋友，双方都点「再来一盘」会交换先后手。';

        modal.hidden = false;
        byId('room-modal-ok').focus();

        return new Promise(function (resolve) {
            modalResolver = resolve;
        });
    }

    window.QP.ui = {
        byId: byId,
        formatClock: formatClock,
        setText: setText,
        setTone: setTone,
        setDisabled: setDisabled,
        toast: toast,
        netStatus: netStatus,
        copyText: copyText,
        createClock: createClock,
        roomModal: { open: openRoomModal }
    };
})();
