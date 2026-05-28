(function () {
    const config = window.SMS_CONFIG || {};
    const keywordInput = document.getElementById("keyword");
    const refreshBtn = document.getElementById("refresh-btn");
    const tableBody = document.getElementById("sms-table-body");

    function buildApiUrl(path) {
        const url = new URL(path, window.location.origin);
        if (config.deviceId) {
            url.searchParams.set("device_id", config.deviceId);
        }
        if (keywordInput && keywordInput.value.trim()) {
            url.searchParams.set("keyword", keywordInput.value.trim());
        }
        return url.toString();
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
        const response = await fetch(buildApiUrl("/sms/api/sms"), {
            credentials: "same-origin",
        });

        if (response.status === 401) {
            window.location.href = "/sms/login?next=" + encodeURIComponent(window.location.pathname);
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

    function bindDeviceDeleteButtons() {
        document.querySelectorAll(".device-delete-btn").forEach(function (button) {
            button.addEventListener("click", async function (event) {
                event.preventDefault();
                event.stopPropagation();

                const deviceId = button.getAttribute("data-device-id");
                const deviceName = button.getAttribute("data-device-name") || deviceId;
                if (!deviceId) {
                    return;
                }

                const confirmed = window.confirm(
                    "确定删除设备「" + deviceName + "」及其全部短信吗？",
                );
                if (!confirmed) {
                    return;
                }

                const response = await fetch("/sms/api/devices/" + encodeURIComponent(deviceId), {
                    method: "DELETE",
                    credentials: "same-origin",
                });

                if (response.status === 401) {
                    window.location.href = "/sms/login?next=" + encodeURIComponent(window.location.pathname);
                    return;
                }

                if (!response.ok) {
                    window.alert("删除失败，请稍后重试");
                    return;
                }

                const card = button.closest(".device-card");
                if (card) {
                    card.remove();
                }

                const deviceGrid = document.getElementById("device-grid");
                if (deviceGrid && !deviceGrid.querySelector(".device-card")) {
                    deviceGrid.innerHTML = '<div class="empty">暂无设备，请先在 Android 端注册。</div>';
                }

                refreshMessages();
            });
        });
    }

    bindDeviceDeleteButtons();

    if (config.autoRefreshMs) {
        setInterval(refreshMessages, config.autoRefreshMs);
    }
})();
