'use strict';
const $ = (id) => document.getElementById(id);
let editingId = null;
let busy = false;
let allUsers = null;
let requestSequence = 0;
let probing = false;
function apiStatus(state, message, sequence) {
  if (sequence !== requestSequence) return;
  $('api-status').className = 'api-status ' + state;
  $('api-status').textContent = message;
  $('api-checked').textContent = 'Проверено в ' + new Date().toLocaleTimeString('ru-RU');
}
function invalidateList() {
  allUsers = null;
  $('users').replaceChildren();
  for (const id of ['count', 'total-count', 'filtered-count']) $(id).textContent = '—';
}
function applyFilter() {
  if (allUsers === null) return;
  const query = $('search').value.trim().toLocaleLowerCase('ru-RU');
  const filtered = allUsers.filter(user => !query
    || user.name.toLocaleLowerCase('ru-RU').includes(query)
    || user.email.toLocaleLowerCase('ru-RU').includes(query)
    || String(user.id) === query);
  $('total-count').textContent = String(allUsers.length);
  $('filtered-count').textContent = String(filtered.length);
  render(filtered);
  if (!filtered.length && allUsers.length) {
    $('list-state').textContent = 'Ничего не найдено. Измените запрос или нажмите «Сбросить».';
  }
}

function notice(message, error = false) {
  $('notice').textContent = message;
  $('notice').classList.toggle('error', error);
  $('notice').hidden = false;
}
function setBusy(value) {
  busy = value;
  document.querySelectorAll('button, input').forEach((element) => { element.disabled = value; });
  $('user-form').setAttribute('aria-busy', String(value));
}
async function api(path = '', options = {}) {
  const sequence = ++requestSequence;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 20000);
  try {
    const response = await fetch('/api/users' + path, {
      ...options,
      headers: { Accept: 'application/hal+json', ...(options.body ? { 'Content-Type': 'application/json' } : {}) },
      cache: 'no-store',
      signal: controller.signal
    });
    const failedProbe = !path && !options.method && !response.ok;
    const degraded = response.status >= 500 || failedProbe;
    apiStatus(degraded ? 'degraded' : 'online',
      degraded ? 'API отвечает с ошибкой' : 'API доступен', sequence);
    const text = await response.text();
    let data;
    try { data = text ? JSON.parse(text) : null; } catch { data = null; }
    if (!response.ok) {
      const fields = { name: 'Имя', email: 'Email', age: 'Возраст' };
      const details = data?.errors
        ? Object.entries(data.errors).map(([key, value]) => `${fields[key] || key}: ${value}`).join('. ')
        : data?.detail;
      throw new Error(details || `Не удалось выполнить запрос (HTTP ${response.status}).`);
    }
    if (response.status !== 204 && data === null) {
      apiStatus('degraded', 'Некорректный ответ API', sequence);
      throw new Error('Сервер вернул некорректный ответ.');
    }
    if (!path && !options.method) data = data?._embedded?.users;
    if (!path && !options.method && !Array.isArray(data)) {
      apiStatus('degraded', 'Некорректный ответ API', sequence);
      throw new Error('Сервер вернул некорректный список пользователей.');
    }
    return data;
  } catch (error) {
    if (error.name === 'AbortError' || error instanceof TypeError) {
      apiStatus('offline', 'API недоступен', sequence);
      throw new Error('Нет ответа от сервера. Проверьте, что приложение запущено. Перед повторной операцией обновите список: запрос мог успеть выполниться.');
    }
    throw error;
  } finally { clearTimeout(timer); }
}
function resetForm() {
  editingId = null;
  $('user-form').reset();
  $('form-mode').textContent = 'НОВАЯ ЗАПИСЬ';
  $('form-title').textContent = 'Добавить пользователя';
  $('save').textContent = 'Создать пользователя';
  $('cancel').hidden = true;
}
function edit(user) {
  if (busy) return;
  editingId = user.id;
  $('name').value = user.name;
  $('email').value = user.email;
  $('age').value = user.age;
  $('form-mode').textContent = `ЗАПИСЬ № ${user.id}`;
  $('form-title').textContent = 'Редактировать пользователя';
  $('save').textContent = 'Сохранить изменения';
  $('cancel').hidden = false;
  $('notice').hidden = true;
  $('name').focus();
}
function render(users) {
  $('users').replaceChildren();
  $('count').textContent = String(users.length);
  $('table-wrap').hidden = users.length === 0;
  $('list-state').hidden = users.length !== 0;
  $('list-state').textContent = 'Пользователей пока нет. Создайте первого с помощью формы.';
  for (const user of users) {
    const row = document.createElement('tr');
    const cell = (text, className) => {
      const td = document.createElement('td');
      if (text !== undefined) td.textContent = text;
      if (className) td.className = className;
      row.append(td);
      return td;
    };
    cell(user.id);
    const person = cell();
    const name = document.createElement('span');
    name.className = 'user-name'; name.textContent = user.name;
    const email = document.createElement('span');
    email.className = 'user-email'; email.textContent = user.email;
    person.append(name, email);
    cell(user.age);
    // createdAt is the server's local time, without an offset: show it without timezone conversion.
    cell(user.createdAt ? user.createdAt.replace('T', ' ').slice(0, 16) : '—', 'date');
    const actions = document.createElement('div'); actions.className = 'actions';
    const change = document.createElement('button');
    change.type = 'button'; change.className = 'secondary'; change.textContent = 'Изменить';
    change.setAttribute('aria-label', `Изменить пользователя ${user.name}`);
    change.addEventListener('click', () => edit(user));
    const remove = document.createElement('button');
    remove.type = 'button'; remove.className = 'danger'; remove.textContent = 'Удалить';
    remove.setAttribute('aria-label', `Удалить пользователя ${user.name}`);
    remove.addEventListener('click', () => deleteUser(user));
    actions.append(change, remove); cell().append(actions);
    $('users').append(row);
  }
}
async function loadUsers() {
  const users = await api();
  if (!Array.isArray(users)) throw new Error('Сервер вернул некорректный список пользователей.');
  allUsers = users;
  applyFilter();
}
async function afterMutation(message) {
  notice(message);
  try { await loadUsers(); }
  catch (error) {
    $('table-wrap').hidden = true;
    $('list-state').hidden = false;
    $('list-state').textContent = 'Список не обновлён. Нажмите «Обновить список».';
    invalidateList();
    notice(message + ' Но список обновить не удалось. ' + error.message, true);
  }
}
async function refresh() {
  if (busy) return;
  setBusy(true); $('notice').hidden = true;
  try { await loadUsers(); }
  catch (error) {
    notice(error.message, true);
    $('table-wrap').hidden = true; $('list-state').hidden = false;
    invalidateList();
    $('list-state').textContent = 'Не удалось загрузить пользователей. Попробуйте обновить список.';
  } finally { setBusy(false); }
}
async function deleteUser(user) {
  if (busy || !window.confirm(`Удалить пользователя «${user.name}» (ID ${user.id})?`)) return;
  setBusy(true);
  try {
    await api('/' + user.id, { method: 'DELETE' });
    if (editingId === user.id) resetForm();
    await afterMutation('Пользователь удалён.');
  } catch (error) { notice(error.message, true); }
  finally { setBusy(false); }
}
$('user-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  if (busy) return;
  const name = $('name').value.trim();
  const email = $('email').value.trim();
  const age = Number($('age').value);
  if (!name || !email || $('age').value === '' || !Number.isInteger(age) || age < 0 || age > 150) {
    notice('Заполните имя, email и целый возраст от 0 до 150.', true); return;
  }
  const updating = editingId !== null;
  setBusy(true);
  try {
    await api(updating ? '/' + editingId : '', {
      method: updating ? 'PUT' : 'POST', body: JSON.stringify({ name, email, age })
    });
    resetForm();
    await afterMutation(updating ? 'Изменения сохранены.' : 'Пользователь создан.');
  } catch (error) { notice(error.message, true); }
  finally { setBusy(false); }
});
$('search').addEventListener('input', applyFilter);
$('clear-search').addEventListener('click', () => {
  $('search').value = '';
  applyFilter();
  $('search').focus();
});
async function checkApi() {
  if (busy || probing || document.hidden) return;
  probing = true;
  try { await api(); } catch { /* Status is updated by api(); keep the form and list intact. */ }
  finally { probing = false; }
}
setInterval(checkApi, 30000);
document.addEventListener('visibilitychange', () => { if (!document.hidden) checkApi(); });
$('cancel').addEventListener('click', resetForm);
$('refresh').addEventListener('click', refresh);
refresh();
