(function () {
    const config = window.SMS_CONFIG || {};
    const keywordInput = document.getElementById("keyword");
    const refreshBtn = document.getElementById("refresh-btn");
    const tableBody = document.getElementById("sms-table-body");
    const deviceGrid = document.getElementById("device-grid");
    const SMS_BASE = (config.basePath || "/sms").replace(/\/$/, "");

    function smsPath(path) {
        const normalized = path.startsWith("/") ? path : "/" + path;
        return SMS_BASE + normalized;
    }

    function buildApiUrl(path) {
        const url = new URL(smsPath(path), window.location.origin);
        if (config.deviceId) {
            url.searchParams.set("device_id", config.deviceId);
        }
        if (keywordInput && keywordInput.value.trim()) {
            url.searchParams.set("keyword", keywordInput.value.trim());
        }
        return url.toString();
    }

    function redirectToLogin() {
        window.location.href =
            smsPath("/login") + "?next=" + encodeURIComponent(window.location.pathname);
    }

    function formatDisplayDatetime(value) {
        if (!value) {
            return "-";
        }

        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return String(value);
        }

        const pad = function (num) {
            return String(num).padStart(2, "0");
        };

        return (
            date.getFullYear() +
            "-" +
            pad(date.getMonth() + 1) +
            "-" +
            pad(date.getDate()) +
            " " +
            pad(date.getHours()) +
            ":" +
            pad(date.getMinutes()) +
            ":" +
            pad(date.getSeconds())
        );
    }

    function renderRows(items) {
        if (!tableBody) {
            return;
        }

        if (!items.length) {
            const colspan = config.deviceId ? 4 : 5;
            tableBody.innerHTML = `<tr><td colspan="${colspan}" class="empty">暂无短信</td></tr>`;
            return;
        }

        tableBody.innerHTML = items.map(function (msg) {
            const deviceCell = config.deviceId
                ? ""
                : `<td>${escapeHtml(msg.device_name || msg.device_id.slice(0, 8))}</td>`;
            const codeCell = msg.verification_code
                ? `<button class="code-btn" data-code="${escapeHtml(msg.verification_code)}" type="button">${escapeHtml(msg.verification_code)}</button>`
                : `<span class="muted">-</span>`;

            return `
                <tr>
                    ${deviceCell}
                    <td>${escapeHtml(msg.sender)}</td>
                    <td>${codeCell}</td>
                    <td class="body-cell">${escapeHtml(msg.body)}</td>
                    <td>${escapeHtml(formatDisplayDatetime(msg.received_at))}</td>
                </tr>
            `;
        }).join("");

        bindCopyButtons();
    }

    function escapeHtml(value) {
        return String(value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function bindCopyButtons() {
        document.querySelectorAll(".code-btn").forEach(function (button) {
            button.addEventListener("click", function () {
                const code = button.getAttribute("data-code");
                if (!code) {
                    return;
                }
                navigator.clipboard.writeText(code).then(function () {
                    const original = button.textContent;
                    button.textContent = "已复制";
                    setTimeout(function () {
                        button.textContent = original;
                    }, 1200);
                });
            });
        });
    }

    async function refreshMessages() {
        const response = await fetch(buildApiUrl("/api/sms"), {
            credentials: "same-origin",
        });

        if (response.status === 401) {
            redirectToLogin();
            return;
        }

        if (!response.ok) {
            return;
        }

        const data = await response.json();
        renderRows(data.items || []);
    }

    if (refreshBtn) {
        refreshBtn.addEventListener("click", refreshMessages);
    }

    if (keywordInput) {
        keywordInput.addEventListener("keydown", function (event) {
            if (event.key !== "Enter") {
                return;
            }

            if (config.deviceId) {
                const url = new URL(window.location.href);
                url.searchParams.set("keyword", keywordInput.value.trim());
                window.location.href = url.toString();
                return;
            }

            refreshMessages();
        });
    }

    bindCopyButtons();

    const confirmOverlay = document.getElementById("confirm-overlay");
    const confirmMessage = document.getElementById("confirm-message");
    const confirmCancelBtn = document.getElementById("confirm-cancel");
    const confirmOkBtn = document.getElementById("confirm-ok");
    let confirmResolve = null;

    function closeAllDeviceMenus() {
        document.querySelectorAll(".device-menu-panel").forEach(function (panel) {
            panel.hidden = true;
        });
        document.querySelectorAll(".device-menu-trigger").forEach(function (trigger) {
            trigger.setAttribute("aria-expanded", "false");
        });
    }

    function toggleDeviceMenu(trigger) {
        const menuRoot = trigger.closest(".device-card-menu");
        if (!menuRoot) {
            return;
        }

        const panel = menuRoot.querySelector(".device-menu-panel");
        if (!panel) {
            return;
        }

        const willOpen = panel.hidden;
        closeAllDeviceMenus();
        panel.hidden = !willOpen;
        trigger.setAttribute("aria-expanded", willOpen ? "true" : "false");
    }

    function openConfirmDialog(message) {
        if (!confirmOverlay || !confirmMessage) {
            return Promise.resolve(window.confirm(message));
        }

        confirmMessage.textContent = message;
        confirmOverlay.hidden = false;
        confirmOverlay.setAttribute("aria-hidden", "false");
        confirmOverlay.classList.add("is-visible");

        return new Promise(function (resolve) {
            confirmResolve = resolve;
        });
    }

    function closeConfirmDialog(result) {
        if (confirmOverlay) {
            confirmOverlay.hidden = true;
            confirmOverlay.setAttribute("aria-hidden", "true");
            confirmOverlay.classList.remove("is-visible");
        }
        if (typeof confirmResolve === "function") {
            confirmResolve(result);
            confirmResolve = null;
        }
    }

    if (confirmCancelBtn) {
        confirmCancelBtn.addEventListener("click", function () {
            closeConfirmDialog(false);
        });
    }

    if (confirmOkBtn) {
        confirmOkBtn.addEventListener("click", function () {
            closeConfirmDialog(true);
        });
    }

    if (confirmOverlay) {
        confirmOverlay.addEventListener("click", function (event) {
            if (event.target === confirmOverlay) {
                closeConfirmDialog(false);
            }
        });
    }

    document.addEventListener("keydown", function (event) {
        if (event.key !== "Escape") {
            return;
        }

        if (confirmOverlay && confirmOverlay.classList.contains("is-visible")) {
            closeConfirmDialog(false);
            return;
        }

        closeAllDeviceMenus();
    });

    document.addEventListener("mousedown", function (event) {
        if (event.target.closest(".device-card-menu")) {
            return;
        }
        closeAllDeviceMenus();
    });

    async function deleteDevice(button) {
        const deviceId = button.getAttribute("data-device-id");
        const deviceName = button.getAttribute("data-device-name") || deviceId;
        if (!deviceId) {
            return;
        }

        closeAllDeviceMenus();

        const confirmed = await openConfirmDialog(
            "将移除设备「" + deviceName + "」及其全部短信记录，此操作不可恢复。"
        );
        if (!confirmed) {
            return;
        }

        const response = await fetch(smsPath("/api/devices/" + encodeURIComponent(deviceId)), {
            method: "DELETE",
            credentials: "same-origin",
        });

        if (response.status === 401) {
            redirectToLogin();
            return;
        }

        if (!response.ok) {
            window.alert("删除失败，请稍后重试");
            return;
        }

        const card = button.closest(".device-card");
        if (card) {
            card.style.transition = "opacity 0.2s ease, transform 0.2s ease";
            card.style.opacity = "0";
            card.style.transform = "scale(0.98)";
            setTimeout(function () {
                card.remove();
            }, 180);
        }

        if (deviceGrid && !deviceGrid.querySelector(".device-card")) {
            deviceGrid.innerHTML = '<div class="empty">暂无设备，请先在 Android 端注册。</div>';
        }

        refreshMessages();
    }

    if (deviceGrid) {
        deviceGrid.addEventListener("click", function (event) {
            const deleteBtn = event.target.closest(".device-delete-btn");
            if (deleteBtn) {
                event.preventDefault();
                event.stopPropagation();
                deleteDevice(deleteBtn);
                return;
            }

            const trigger = event.target.closest(".device-menu-trigger");
            if (!trigger) {
                return;
            }

            event.preventDefault();
            event.stopPropagation();
            toggleDeviceMenu(trigger);
        });
    }

    if (config.autoRefreshMs) {
        setInterval(refreshMessages, config.autoRefreshMs);
    }
})();
