// Web client of Sandbox Bank. Plain ES modules, no build step.
// User supplied text is only ever written with textContent, never as HTML.

const API = '/api/v1';
const TOKEN_KEY = 'sandbox-bank.token';
const STATEMENT_PAGE_SIZE = 10;
const AMOUNT_PATTERN = /^(0|[1-9]\d{0,12})(\.\d{1,2})?$/;
const ACCOUNT_NUMBER_PATTERN = /^GE\d{2}ZZ\d{16}$/;

const state = {
  token: sessionStorage.getItem(TOKEN_KEY),
  customer: null,
  accounts: [],
  statement: { accountId: null, page: 0, totalPages: 0 },
  faults: [],
};

const $ = (selector, root = document) => root.querySelector(selector);

// ---------------------------------------------------------------- API access

class ApiFailure extends Error {
  constructor(status, body) {
    super(body?.message ?? 'The request failed');
    this.status = status;
    this.code = body?.code;
    this.details = body?.details ?? [];
  }
}

/** The request never produced an answer, so its outcome is unknown. */
class NoAnswer extends Error {}

async function api(method, path, { body, headers = {} } = {}) {
  const requestHeaders = { ...headers };
  if (body !== undefined) {
    requestHeaders['Content-Type'] = 'application/json';
  }
  if (state.token) {
    requestHeaders.Authorization = `Bearer ${state.token}`;
  }
  let response;
  try {
    response = await fetch(API + path, {
      method,
      headers: requestHeaders,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new NoAnswer();
  }
  const payload = await response.json().catch(() => null);
  if (response.status === 401 && state.token) {
    endSession('Your session has expired. Sign in again.');
  }
  if (!response.ok) {
    throw new ApiFailure(response.status, payload);
  }
  return payload;
}

// ---------------------------------------------------------------- messages and field errors

function clearMessages() {
  $('#notice').textContent = '';
  $('#alert').textContent = '';
}

function notify(text) {
  $('#alert').textContent = '';
  $('#notice').textContent = text;
}

function alertUser(text) {
  $('#notice').textContent = '';
  $('#alert').textContent = text;
}

function clearFieldErrors(form) {
  for (const error of form.querySelectorAll('.field-error')) {
    error.textContent = '';
  }
  for (const input of form.querySelectorAll('[aria-invalid]')) {
    input.removeAttribute('aria-invalid');
  }
}

/** Shows a message under the field and links it to the input for assistive technology. */
function fieldError(form, field, text) {
  const error = $(`[data-error-for="${field}"]`, form);
  if (!error) {
    alertUser(text);
    return;
  }
  error.textContent = text;
  const input = error.previousElementSibling?.matches('.hint')
    ? error.previousElementSibling.previousElementSibling
    : error.previousElementSibling;
  if (input) {
    error.id ||= `${input.id}-error`;
    input.setAttribute('aria-invalid', 'true');
    const describedBy = new Set((input.getAttribute('aria-describedby') ?? '').split(' ').filter(Boolean));
    describedBy.add(error.id);
    input.setAttribute('aria-describedby', [...describedBy].join(' '));
  }
}

function showServerProblems(form, failure, labels) {
  if (failure.code === 'VALIDATION_ERROR' && failure.details.length > 0) {
    for (const { field, issue } of failure.details) {
      fieldError(form, field, `${labels[field] ?? field} ${issue}`);
    }
    $('[aria-invalid="true"]', form)?.focus();
  } else {
    alertUser(`${failure.message}.`);
  }
}

// ---------------------------------------------------------------- formatting

/** Adds thousands separators. Works on the text, so the amount never becomes a float. */
function formatAmount(amount) {
  const [whole, fraction] = amount.split('.');
  return `${whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${fraction}`;
}

const formatDate = (iso) =>
  new Date(iso).toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' });

const isPositiveAmount = (text) => AMOUNT_PATTERN.test(text) && /[1-9]/.test(text);

function element(tag, attributes = {}, text = '') {
  const node = document.createElement(tag);
  for (const [name, value] of Object.entries(attributes)) {
    node.setAttribute(name, value);
  }
  node.textContent = text;
  return node;
}

// ---------------------------------------------------------------- views and session

const VIEWS = { login: 'Sign in', register: 'Create an account', dashboard: 'Your accounts' };

function show(view) {
  for (const name of Object.keys(VIEWS)) {
    $(`#view-${name}`).hidden = name !== view;
  }
  document.title = `${VIEWS[view]} | Sandbox Bank`;
  $('#session').hidden = view !== 'dashboard';
  $(`#view-${view} h1`).focus();
}

function endSession(message) {
  sessionStorage.removeItem(TOKEN_KEY);
  Object.assign(state, { token: null, customer: null, accounts: [] });
  state.statement = { accountId: null, page: 0, totalPages: 0 };
  location.hash = '#/login';
  show('login');
  if (message) {
    alertUser(message);
  }
}

async function startSession(token) {
  state.token = token;
  sessionStorage.setItem(TOKEN_KEY, token);
  location.hash = '#/accounts';
  await openDashboard();
}

async function openDashboard() {
  state.customer = await api('GET', '/customers/me');
  $('#customer-name').textContent = state.customer.fullName;
  await loadAccounts();
  show('dashboard');
  await loadStatement(0);
}

async function route() {
  const wantsRegistration = location.hash === '#/register';
  if (!state.token) {
    show(wantsRegistration ? 'register' : 'login');
    return;
  }
  if (!state.customer) {
    try {
      await openDashboard();
    } catch (failure) {
      if (!(failure instanceof ApiFailure && failure.status === 401)) {
        alertUser('The bank is not answering. Reload the page to try again.');
      }
    }
    return;
  }
  show('dashboard');
}

// ---------------------------------------------------------------- accounts

async function loadAccounts() {
  state.accounts = (await api('GET', '/accounts')).items;
  if (!state.accounts.some((account) => account.id === state.statement.accountId)) {
    state.statement.accountId = state.accounts[0]?.id ?? null;
  }
  renderAccounts();
}

function renderAccounts() {
  const rows = state.accounts.map((account) => {
    const frozen = account.status === 'FROZEN';
    const row = element('tr', { 'data-account-id': account.id, ...(frozen ? { class: 'frozen' } : {}) });
    const statement = element(
      'button',
      { type: 'button', class: 'secondary', 'aria-label': `Statement of ${account.number}` },
      'Statement',
    );
    statement.addEventListener('click', () => {
      clearMessages();
      state.statement.accountId = account.id;
      loadStatement(0);
    });
    const toggle = element(
      'button',
      { type: 'button', class: 'secondary', 'aria-label': `${frozen ? 'Unfreeze' : 'Freeze'} ${account.number}` },
      frozen ? 'Unfreeze' : 'Freeze',
    );
    toggle.addEventListener('click', () => setFrozen(account, !frozen));
    const actions = element('td');
    const group = element('div', { class: 'actions' });
    group.append(statement, toggle);
    actions.append(group);
    row.append(
      element('th', { scope: 'row', class: 'mono' }, account.number),
      element('td', {}, account.currency),
      element('td', { class: 'amount', 'data-testid': 'balance' }, formatAmount(account.balance)),
      element('td', { 'data-testid': 'status' }, frozen ? 'Frozen' : 'Active'),
      actions,
    );
    return row;
  });
  $('#accounts tbody').replaceChildren(...rows);
  $('#accounts-panel').hidden = rows.length === 0;
  $('#no-accounts').hidden = rows.length > 0;

  for (const select of [$('#transfer-source'), $('#deposit-account')]) {
    const selected = select.value;
    select.replaceChildren(
      ...state.accounts.map((account) =>
        element('option', { value: account.id }, `${account.number} (${account.currency})`),
      ),
    );
    if (state.accounts.some((account) => account.id === selected)) {
      select.value = selected;
    }
  }
}

async function setFrozen(account, frozen) {
  clearMessages();
  try {
    await api('POST', `/accounts/${account.id}/${frozen ? 'freeze' : 'unfreeze'}`);
    await loadAccounts();
    notify(frozen ? `Account ${account.number} is frozen.` : `Account ${account.number} is active again.`);
  } catch (failure) {
    reportUnexpected(failure);
  }
}

// ---------------------------------------------------------------- statement

async function loadStatement(page) {
  const { accountId } = state.statement;
  const account = state.accounts.find((candidate) => candidate.id === accountId);
  $('#statement-account').textContent = account ? `${account.number} (${account.currency})` : '';
  if (!account) {
    renderStatement({ items: [], page: 0, totalPages: 0 });
    return;
  }
  try {
    renderStatement(await api('GET', `/accounts/${accountId}/transactions?page=${page}&size=${STATEMENT_PAGE_SIZE}`));
  } catch (failure) {
    reportUnexpected(failure);
  }
}

function renderStatement({ items, page, totalPages }) {
  state.statement.page = page;
  state.statement.totalPages = totalPages;
  const rows = items.map((entry) => {
    const row = element('tr');
    const amount = formatAmount(entry.amount);
    row.append(
      element('td', {}, formatDate(entry.createdAt)),
      element('td', {}, entry.type === 'DEPOSIT' ? 'Deposit' : 'Transfer'),
      element('td', { 'data-testid': 'description' }, entry.description ?? ''),
      element('td', { class: 'amount', 'data-testid': 'paid-out' }, entry.direction === 'DEBIT' ? amount : ''),
      element('td', { class: 'amount', 'data-testid': 'paid-in' }, entry.direction === 'CREDIT' ? amount : ''),
      element('td', { class: 'amount', 'data-testid': 'balance-after' }, formatAmount(entry.balanceAfter)),
    );
    return row;
  });
  $('#statement tbody').replaceChildren(...rows);
  $('#statement-panel').hidden = rows.length === 0;
  $('#statement-empty').hidden = rows.length > 0;
  $('#statement-page').textContent = totalPages > 0 ? `Page ${page + 1} of ${totalPages}` : '';
  $('#statement-previous').disabled = page <= 0;
  $('#statement-next').disabled = page >= totalPages - 1;
}

// ---------------------------------------------------------------- money movement

// One key per intended movement. It survives an unanswered request, so pressing the button
// again repeats the same request and the bank moves the money only once.
const pendingKeys = new WeakMap();

function idempotencyKeyFor(form, request) {
  const fingerprint = JSON.stringify(request);
  const pending = pendingKeys.get(form);
  if (pending?.fingerprint === fingerprint) {
    return pending.key;
  }
  const key = crypto.randomUUID();
  pendingKeys.set(form, { fingerprint, key });
  return key;
}

async function moveMoney(form, { path, request, labels, completed, rejected }) {
  const button = $('button[type="submit"]', form);
  button.disabled = true;
  try {
    const transfer = await api('POST', path, {
      body: request,
      headers: { 'Idempotency-Key': idempotencyKeyFor(form, request) },
    });
    pendingKeys.delete(form);
    for (const input of form.querySelectorAll('input')) {
      input.value = '';
    }
    await refreshAfterMoneyMoved();
    notify(completed(transfer));
  } catch (failure) {
    if (failure instanceof ApiFailure && failure.status < 500) {
      pendingKeys.delete(form);
      if (failure.code === 'VALIDATION_ERROR') {
        showServerProblems(form, failure, labels);
      } else if (failure.status !== 401) {
        alertUser(`${rejected} ${failure.message}.`);
      }
    } else {
      alertUser('The bank did not confirm the result. Press the button again. The money will not move twice.');
    }
  } finally {
    button.disabled = false;
  }
}

async function refreshAfterMoneyMoved() {
  if (!state.faults.includes('UI_STALE_BALANCE')) {
    await loadAccounts();
  }
  await loadStatement(0);
}

function describeTransfer(transfer) {
  const sent = `${formatAmount(transfer.sourceAmount)} ${transfer.sourceCurrency} sent`;
  if (transfer.fxRate === null) {
    return `Transfer completed. ${sent}.`;
  }
  const received = `${formatAmount(transfer.targetAmount)} ${transfer.targetCurrency} received`;
  return `Transfer completed. ${sent}, ${received} at rate ${transfer.fxRate}.`;
}

function reportUnexpected(failure) {
  if (failure instanceof ApiFailure && failure.status === 401) {
    return; // the session has ended and the sign in view explains why
  }
  alertUser(failure instanceof ApiFailure ? `${failure.message}.` : 'The bank is not answering. Try again.');
}

// ---------------------------------------------------------------- form handlers

$('#login-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  clearMessages();
  clearFieldErrors(form);
  const email = $('#login-email').value.trim();
  const password = $('#login-password').value;
  if (!email) {
    fieldError(form, 'email', 'Enter your email');
  }
  if (!password) {
    fieldError(form, 'password', 'Enter your password');
  }
  if (!email || !password) {
    $('[aria-invalid="true"]', form).focus();
    return;
  }
  try {
    const { accessToken } = await api('POST', '/auth/login', { body: { email, password } });
    form.reset();
    await startSession(accessToken);
  } catch (failure) {
    if (failure instanceof ApiFailure && failure.code === 'INVALID_CREDENTIALS') {
      alertUser(`${failure.message}.`);
      $('#login-password').focus();
    } else {
      reportUnexpected(failure);
    }
  }
});

$('#register-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  clearMessages();
  clearFieldErrors(form);
  const registration = {
    fullName: $('#register-name').value.trim(),
    email: $('#register-email').value.trim(),
    password: $('#register-password').value,
  };
  try {
    await api('POST', '/customers', { body: registration });
    const { accessToken } = await api('POST', '/auth/login', {
      body: { email: registration.email, password: registration.password },
    });
    form.reset();
    await startSession(accessToken);
  } catch (failure) {
    if (failure instanceof ApiFailure && failure.code === 'EMAIL_ALREADY_REGISTERED') {
      fieldError(form, 'email', 'This email is already registered');
      $('#register-email').focus();
    } else if (failure instanceof ApiFailure) {
      showServerProblems(form, failure, { fullName: 'Full name', email: 'Email', password: 'Password' });
    } else {
      reportUnexpected(failure);
    }
  }
});

$('#sign-out').addEventListener('click', () => {
  endSession();
  notify('You have signed out.');
});

$('#transfer-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  clearMessages();
  clearFieldErrors(form);
  const request = {
    sourceAccountId: $('#transfer-source').value,
    targetAccountNumber: $('#transfer-target').value.trim().toUpperCase(),
    amount: $('#transfer-amount').value.trim(),
  };
  const description = $('#transfer-description').value.trim();
  if (description) {
    request.description = description;
  }
  if (!request.sourceAccountId) {
    fieldError(form, 'sourceAccountId', 'Open an account first');
  }
  if (!ACCOUNT_NUMBER_PATTERN.test(request.targetAccountNumber)) {
    fieldError(form, 'targetAccountNumber', 'Enter an account number such as GE00ZZ0000000000000000');
  }
  if (!isPositiveAmount(request.amount)) {
    fieldError(form, 'amount', 'Enter an amount such as 25.00');
  }
  const firstInvalid = $('[aria-invalid="true"]', form);
  if (firstInvalid) {
    firstInvalid.focus();
    return;
  }
  await moveMoney(form, {
    path: '/transfers',
    request,
    labels: { targetAccountNumber: 'Account number', amount: 'Amount', description: 'Description' },
    completed: describeTransfer,
    rejected: 'Transfer rejected.',
  });
});

$('#deposit-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  clearMessages();
  clearFieldErrors(form);
  const accountId = $('#deposit-account').value;
  const amount = $('#deposit-amount').value.trim();
  if (!accountId) {
    alertUser('Open an account first.');
    return;
  }
  if (!isPositiveAmount(amount)) {
    fieldError(form, 'amount', 'Enter an amount such as 25.00');
    $('#deposit-amount').focus();
    return;
  }
  await moveMoney(form, {
    path: `/accounts/${accountId}/deposits`,
    request: { amount },
    labels: { amount: 'Amount' },
    completed: (transfer) => `Funds added. ${formatAmount(transfer.targetAmount)} ${transfer.targetCurrency} received.`,
    rejected: 'Funds not added.',
  });
});

$('#open-account-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  clearMessages();
  try {
    const account = await api('POST', '/accounts', { body: { currency: $('#open-account-currency').value } });
    await loadAccounts();
    notify(`Account ${account.number} (${account.currency}) is open.`);
  } catch (failure) {
    if (failure instanceof ApiFailure && failure.status === 422) {
      alertUser(`The account was not opened. ${failure.message}.`);
    } else {
      reportUnexpected(failure);
    }
  }
});

$('#statement-previous').addEventListener('click', () => loadStatement(state.statement.page - 1));
$('#statement-next').addEventListener('click', () => loadStatement(state.statement.page + 1));

// ---------------------------------------------------------------- start

window.addEventListener('hashchange', route);

fetch('/health')
  .then((response) => response.json())
  .then((health) => {
    state.faults = health.faults ?? [];
  })
  .catch(() => {})
  .finally(route);
