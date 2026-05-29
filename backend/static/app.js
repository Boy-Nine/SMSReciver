(function () {
    const config = window.SMS_CONFIG || {};
    const keywordInput = document.getElementById("keyword");
    const refreshBtn = document.getElementById("refresh-btn");
    const tableBody = document.getElementById("sms-table-body");
    const deviceGrid = document.getElementById("device-grid");
    const pageSizeSelect = document.getElementById("page-size-select");
    const prevPageBtn = document.getElementById("prev-page-btn");
    const nextPageBtn = document.getElementById("next-page-btn");
    const pageIndicator = document.getElementById("page-indicator");
    const paginationInfo = document.getElementById("pagination-info");
    const headerSubtitle = document.getElementById("header-subtitle");
    const SMS_BASE = (config.basePath || "/sms").replace(/\/$/, "");
    const DEFAULT_PAGE_SIZE = 10;

    let currentPage = config.currentPage || 1;
    let pageSize = config.pageSize || DEFAULT_PAGE_SIZE;
    let totalCount = config.totalMessages || 0;

    if (pageSizeSelect) {
        pageSizeSelect.value = String(pageSize);
    }

    function smsPath(path) {
        const normalized = path.startsWith("/") ? path : "/" + path;
        return SMS_BASE + normalized;
    }

    function getTotalPages() {
        if (totalCount <= 0) {
            return 1;
        }
        return Math.ceil(totalCount / pageSize);
    }

    function buildApiUrl(path) {
        const url = new URL(smsPath(path), window.location.origin);
        if (config.deviceId) {
            url.searchParams.set("device_id", config.deviceId);
        }
        if (keywordInput && keywordInput.value.trim()) {
            url.searchParams.set("keyword", keywordInput.value.trim());
        }
        url.searchParams.set("limit", String(pageSize));
        url.searchParams.set("offset", String((currentPage - 1) * pageSize));
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

        const text = String(value).trim();
        if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(text)) {
            return text;
        }

        const date = new Date(text);
        if (Number.isNaN(date.getTime())) {
            return text;
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

    function renderPagination() {
        const totalPages = getTotalPages();
        if (currentPage > totalPages) {
            currentPage = totalPages;
        }
        if (currentPage < 1) {
            currentPage = 1;
        }

        if (pageIndicator) {
            pageIndicator.textContent = currentPage + " / " + totalPages;
        }
        if (paginationInfo) {
            paginationInfo.textContent = "共 " + totalCount + " 条";
        }
        if (prevPageBtn) {
            prevPageBtn.disabled = currentPage <= 1;
        }
        if (nextPageBtn) {
            nextPageBtn.disabled = currentPage >= totalPages;
        }
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

    function updateTotalSummary() {
        if (headerSubtitle) {
            headerSubtitle.textContent = "局域网短信汇总 · 共 " + totalCount + " 条";
        }
    }

    function buildDeviceCardHtml(device) {
        const deviceId = escapeHtml(device.device_id);
        const deviceName = escapeHtml(device.device_name);
        const phoneNumber = device.phone_number || "";
        const phoneAttr = escapeHtml(phoneNumber);
        const popoverText = phoneNumber ? escapeHtml(phoneNumber) : "未设置手机号";
        const deviceUrl = escapeHtml(smsPath("/device/" + device.device_id));
        const lastSeen = device.last_seen_at
            ? "<span>最近活跃: " + escapeHtml(device.last_seen_at) + "</span>"
            : '<span class="muted">暂无活跃记录</span>';
        const preview = device.latest_sms_preview
            ? '<div class="device-preview">' + escapeHtml(device.latest_sms_preview) + "</div>"
            : '<div class="device-preview muted">暂无短信</div>';

        return (
            '<div class="device-card" data-device-id="' +
            deviceId +
            '" data-phone="' +
            phoneAttr +
            '">' +
            '<div class="device-phone-popover">' +
            popoverText +
            "</div>" +
            '<div class="device-card-view">' +
            '<div class="device-card-head">' +
            '<a class="device-name-link" href="' +
            deviceUrl +
            '">' +
            deviceName +
            "</a>" +
            '<div class="device-card-actions">' +
            '<button class="btn-edit device-edit-btn" type="button" data-device-id="' +
            deviceId +
            '">编辑</button>' +
            '<button class="btn-delete device-delete-btn" type="button" data-device-id="' +
            deviceId +
            '" data-device-name="' +
            deviceName +
            '">删除</button>' +
            "</div></div>" +
            '<a class="device-card-body" href="' +
            deviceUrl +
            '">' +
            '<div class="device-meta">' +
            lastSeen +
            "</div>" +
            preview +
            "</a></div>" +
            '<form class="device-card-edit">' +
            '<label class="device-edit-field"><span>设备名称</span>' +
            '<input class="device-edit-name" type="text" maxlength="100" value="' +
            deviceName +
            '" required></label>' +
            '<label class="device-edit-field"><span>手机号</span>' +
            '<input class="device-edit-phone" type="text" maxlength="100" value="' +
            phoneAttr +
            '" placeholder="可留空"></label>' +
            '<div class="device-edit-actions">' +
            '<button class="btn-save device-save-btn" type="button">保存</button>' +
            '<button class="btn-cancel device-cancel-btn" type="button">取消</button>' +
            "</div></form></div>"
        );
    }

    function renderDeviceGrid(devices) {
        if (!deviceGrid) {
            return;
        }

        if (!devices.length) {
            deviceGrid.innerHTML = '<div class="empty">暂无设备，请先在 Android 端注册。</div>';
            return;
        }

        deviceGrid.innerHTML = devices.map(buildDeviceCardHtml).join("");
    }

    async function refreshDevices() {
        if (!deviceGrid || config.deviceId) {
            return;
        }

        if (deviceGrid.querySelector(".device-card.is-editing")) {
            return;
        }

        const response = await fetch(smsPath("/api/devices"), {
            credentials: "same-origin",
        });

        if (response.status === 401) {
            redirectToLogin();
            return;
        }

        if (!response.ok) {
            return;
        }

        const devices = await response.json();
        renderDeviceGrid(devices || []);
    }

    async function refreshMessages() {
        if (!tableBody) {
            return;
        }

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
        totalCount = data.total || 0;
        renderRows(data.items || []);
        renderPagination();
        updateTotalSummary();
    }

    async function refreshDashboard() {
        await Promise.all([refreshMessages(), refreshDevices()]);
    }

    if (refreshBtn) {
        refreshBtn.addEventListener("click", function () {
            if (deviceGrid && !config.deviceId) {
                refreshDashboard();
                return;
            }
            refreshMessages();
        });
    }

    if (pageSizeSelect) {
        pageSizeSelect.addEventListener("change", function () {
            pageSize = parseInt(pageSizeSelect.value, 10) || DEFAULT_PAGE_SIZE;
            currentPage = 1;
            refreshMessages();
        });
    }

    if (prevPageBtn) {
        prevPageBtn.addEventListener("click", function () {
            if (currentPage <= 1) {
                return;
            }
            currentPage -= 1;
            refreshMessages();
        });
    }

    if (nextPageBtn) {
        nextPageBtn.addEventListener("click", function () {
            if (currentPage >= getTotalPages()) {
                return;
            }
            currentPage += 1;
            refreshMessages();
        });
    }

    if (keywordInput) {
        keywordInput.addEventListener("keydown", function (event) {
            if (event.key !== "Enter") {
                return;
            }

            currentPage = 1;
            refreshMessages();
        });
    }

    bindCopyButtons();
    renderPagination();

    async function deleteDevice(button) {
        const deviceId = button.getAttribute("data-device-id");
        const deviceName = button.getAttribute("data-device-name") || deviceId;
        if (!deviceId) {
            return;
        }

        const confirmed = window.confirm(
            "确定删除设备「" + deviceName + "」及其全部短信吗？"
        );
        if (!confirmed) {
            return;
        }

        button.disabled = true;
        button.textContent = "删除中…";

        try {
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
                button.disabled = false;
                button.textContent = "删除";
                return;
            }

            const card = button.closest(".device-card");
            if (card) {
                card.remove();
            }

            if (deviceGrid && !deviceGrid.querySelector(".device-card")) {
                deviceGrid.innerHTML = '<div class="empty">暂无设备，请先在 Android 端注册。</div>';
            }

            refreshDashboard();
        } catch (error) {
            window.alert("删除失败，请检查网络后重试");
            button.disabled = false;
            button.textContent = "删除";
        }
    }

    if (deviceGrid) {
        deviceGrid.addEventListener("click", function (event) {
            const saveBtn = event.target.closest(".device-save-btn");
            if (saveBtn) {
                event.preventDefault();
                event.stopPropagation();
                saveDeviceCard(saveBtn.closest(".device-card"));
                return;
            }

            const cancelBtn = event.target.closest(".device-cancel-btn");
            if (cancelBtn) {
                event.preventDefault();
                event.stopPropagation();
                closeDeviceEdit(cancelBtn.closest(".device-card"));
                return;
            }

            const editBtn = event.target.closest(".device-edit-btn");
            if (editBtn) {
                event.preventDefault();
                event.stopPropagation();
                openDeviceEdit(editBtn.closest(".device-card"));
                return;
            }

            const deleteBtn = event.target.closest(".device-delete-btn");
            if (!deleteBtn) {
                return;
            }

            event.preventDefault();
            event.stopPropagation();
            deleteDevice(deleteBtn);
        });
    }

    function openDeviceEdit(card) {
        if (!card) {
            return;
        }

        card.classList.add("is-editing");
        const nameInput = card.querySelector(".device-edit-name");
        if (nameInput) {
            nameInput.focus();
            nameInput.select();
        }
    }

    function closeDeviceEdit(card) {
        if (!card) {
            return;
        }

        const nameInput = card.querySelector(".device-edit-name");
        const phoneInput = card.querySelector(".device-edit-phone");
        const nameLink = card.querySelector(".device-name-link");
        if (nameInput && nameLink) {
            nameInput.value = nameLink.textContent.trim();
        }
        if (phoneInput) {
            phoneInput.value = card.getAttribute("data-phone") || "";
        }

        card.classList.remove("is-editing");
    }

    function updateDeviceCardView(card, device) {
        if (!card || !device) {
            return;
        }

        const phoneNumber = device.phone_number || "";
        card.setAttribute("data-phone", phoneNumber);

        const popover = card.querySelector(".device-phone-popover");
        if (popover) {
            popover.textContent = phoneNumber || "未设置手机号";
        }

        const nameLink = card.querySelector(".device-name-link");
        if (nameLink) {
            nameLink.textContent = device.device_name;
        }

        const deleteBtn = card.querySelector(".device-delete-btn");
        if (deleteBtn) {
            deleteBtn.setAttribute("data-device-name", device.device_name);
        }

        const nameInput = card.querySelector(".device-edit-name");
        const phoneInput = card.querySelector(".device-edit-phone");
        if (nameInput) {
            nameInput.value = device.device_name;
        }
        if (phoneInput) {
            phoneInput.value = phoneNumber;
        }
    }

    async function saveDeviceCard(card) {
        if (!card) {
            return;
        }

        const deviceId = card.getAttribute("data-device-id");
        const nameInput = card.querySelector(".device-edit-name");
        const phoneInput = card.querySelector(".device-edit-phone");
        const saveBtn = card.querySelector(".device-save-btn");
        if (!deviceId || !nameInput) {
            return;
        }

        const deviceName = nameInput.value.trim();
        if (!deviceName) {
            window.alert("设备名称不能为空");
            nameInput.focus();
            return;
        }

        if (saveBtn) {
            saveBtn.disabled = true;
            saveBtn.textContent = "保存中…";
        }

        try {
            const response = await fetch(smsPath("/api/devices/" + encodeURIComponent(deviceId)), {
                method: "PATCH",
                credentials: "same-origin",
                headers: {
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    device_name: deviceName,
                    phone_number: phoneInput ? phoneInput.value.trim() : "",
                }),
            });

            if (response.status === 401) {
                redirectToLogin();
                return;
            }

            if (!response.ok) {
                window.alert("保存失败，请稍后重试");
                return;
            }

            const device = await response.json();
            updateDeviceCardView(card, device);
            card.classList.remove("is-editing");
            refreshDashboard();
        } catch (error) {
            window.alert("保存失败，请检查网络后重试");
        } finally {
            if (saveBtn) {
                saveBtn.disabled = false;
                saveBtn.textContent = "保存";
            }
        }
    }

    if (deviceGrid) {
        deviceGrid.addEventListener("keydown", function (event) {
            if (event.key !== "Enter") {
                return;
            }

            const card = event.target.closest(".device-card.is-editing");
            if (!card) {
                return;
            }

            event.preventDefault();
            saveDeviceCard(card);
        });
    }

    if (config.autoRefreshMs) {
        setInterval(function () {
            if (deviceGrid && !config.deviceId) {
                refreshDashboard();
                return;
            }
            refreshMessages();
        }, config.autoRefreshMs);
    }
})();
