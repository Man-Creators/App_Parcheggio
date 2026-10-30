/* Dov'è l'auto — logica dell'interfaccia.
   Nel telefono parla con la parte Android tramite window.Native;
   aperta in un browser normale usa una versione di prova (localStorage + GPS del browser). */
(function () {
  'use strict';

  // ---------- Ponte con Android (o simulazione nel browser) ----------
  var Native = window.Native || (function () {
    var KEY = 'parcheggi-demo';
    function load() { try { return JSON.parse(localStorage.getItem(KEY) || '[]'); } catch (e) { return []; } }
    function store(a) { try { localStorage.setItem(KEY, JSON.stringify(a)); } catch (e) {} }
    function geo(ok, ko) {
      if (!navigator.geolocation) return ko('Posizione non disponibile');
      navigator.geolocation.getCurrentPosition(function (p) { ok(p.coords); },
        function () { ok({ latitude: 45.6055, longitude: 13.7660, accuracy: 12 }); },
        { enableHighAccuracy: true, timeout: 8000 });
    }
    return {
      getSpots: function () { return JSON.stringify(load()); },
      saveNow: function () {
        App.onSaving();
        geo(function (c) {
          var s = { id: String(Date.now()), lat: c.latitude, lng: c.longitude, acc: Math.round(c.accuracy), time: Date.now(), address: '', note: '' };
          var a = load(); a.unshift(s); store(a.slice(0, 50)); App.onSaved(s);
          setTimeout(function () { s.address = 'Via di prova 12, Muggia'; Native.__upd(s); App.onUpdated(s); }, 700);
        }, App.onError);
      },
      __upd: function (s) { var a = load(); a.forEach(function (x, i) { if (x.id === s.id) a[i] = s; }); store(a); },
      locateMe: function () { geo(function (c) { App.onMyLocation(c.latitude, c.longitude, c.accuracy); }, App.onMyLocationError); },
      deleteSpot: function (id) { store(load().filter(function (x) { return x.id !== id; })); },
      setNote: function (id, n) { var a = load(); a.forEach(function (x) { if (x.id === id) x.note = n; }); store(a); },
      movePosition: function (id, lat, lng) { var a = load(); a.forEach(function (x) { if (x.id === id) { x.lat = lat; x.lng = lng; } }); store(a); },
      navigate: function (lat, lng) { window.open('https://www.google.com/maps/dir/?api=1&travelmode=walking&destination=' + lat + ',' + lng); },
      share: function (lat, lng) { App.toast('Link copiato'); try { navigator.clipboard.writeText('https://maps.google.com/?q=' + lat + ',' + lng); } catch (e) {} },
      launchAction: function () { return ''; }
    };
  })();

  // ---------- Utilità ----------
  var $ = function (id) { return document.getElementById(id); };
  var MESI = ['gen', 'feb', 'mar', 'apr', 'mag', 'giu', 'lug', 'ago', 'set', 'ott', 'nov', 'dic'];
  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function sameDay(a, b) { return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate(); }
  function when(t) {
    var d = new Date(t), now = new Date(), y = new Date(now.getTime() - 864e5);
    var h = pad(d.getHours()) + ':' + pad(d.getMinutes());
    if (sameDay(d, now)) return 'oggi ' + h;
    if (sameDay(d, y)) return 'ieri ' + h;
    return d.getDate() + ' ' + MESI[d.getMonth()] + (d.getFullYear() !== now.getFullYear() ? ' ' + d.getFullYear() : '') + ' ' + h;
  }
  function ago(t) {
    var m = Math.round((Date.now() - t) / 60000);
    if (m < 1) return 'adesso';
    if (m < 60) return m + ' min fa';
    var h = Math.floor(m / 60);
    if (h < 24) return h + (h === 1 ? ' ora fa' : ' ore fa');
    var g = Math.floor(h / 24);
    return g + (g === 1 ? ' giorno fa' : ' giorni fa');
  }
  function place(s) { return s.address || (s.lat.toFixed(5) + ', ' + s.lng.toFixed(5)); }
  function dist(a, b) {
    var R = 6371000, r = Math.PI / 180;
    var dLat = (b.lat - a.lat) * r, dLng = (b.lng - a.lng) * r;
    var x = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(a.lat * r) * Math.cos(b.lat * r) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return 2 * R * Math.asin(Math.sqrt(x));
  }
  function fmtDist(m) { return m < 1000 ? 'a ' + Math.round(m / 10) * 10 + ' m' : 'a ' + (m / 1000).toFixed(1).replace('.', ',') + ' km'; }
  function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }

  // ---------- Stato ----------
  var spots = [];
  var shownId = null;        // parcheggio mostrato nella scheda
  var me = null;             // mia posizione {lat,lng}
  var fixing = false;

  // ---------- Mappa ----------
  var map = L.map('map', { zoomControl: false, attributionControl: true }).setView([45.6055, 13.7660], 14);
  L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
    maxZoom: 20, subdomains: 'abcd',
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/attributions">CARTO</a>'
  }).addTo(map);
  map.attributionControl.setPrefix(false);

  var pinSvg = '<svg viewBox="0 0 44 54"><path fill="#8A2CFF" d="M22 1C10.4 1 1 10.4 1 22c0 15.6 21 31 21 31s21-15.4 21-31C43 10.4 33.6 1 22 1z"/>' +
    '<path fill="#fff" d="M30.6 14.8a1.6 1.6 0 0 0-1.5-1.1H14.9a1.6 1.6 0 0 0-1.5 1.1L11 21.7v9.1c0 .6.5 1.1 1.1 1.1h1.2c.6 0 1.1-.5 1.1-1.1v-1.2h15.2v1.2c0 .6.5 1.1 1.1 1.1h1.2c.6 0 1.1-.5 1.1-1.1v-9.1l-2.4-6.9zM15.3 26.7a1.7 1.7 0 1 1 0-3.4 1.7 1.7 0 0 1 0 3.4zm13.4 0a1.7 1.7 0 1 1 0-3.4 1.7 1.7 0 0 1 0 3.4zM13.6 20.6l1.7-5h13.4l1.7 5H13.6z"/></svg>';
  var carIcon = L.divIcon({ className: '', html: '<div class="car-pin"><span class="pulse"></span>' + pinSvg + '</div>', iconSize: [44, 54], iconAnchor: [22, 54] });
  var meIcon = L.divIcon({ className: '', html: '<div class="me-dot"></div>', iconSize: [18, 18], iconAnchor: [9, 9] });
  var carMarker = null, meMarker = null;

  // La parte visibile della mappa è sopra la scheda: sposto il centro un po' in alto
  function focus(lat, lng, zoom) {
    var z = zoom || Math.max(map.getZoom(), 17);
    var p = map.project([lat, lng], z).add([0, map.getSize().y * 0.12]);
    map.setView(map.unproject(p, z), z, { animate: true });
  }

  function current() {
    for (var i = 0; i < spots.length; i++) if (spots[i].id === shownId) return spots[i];
    return spots[0] || null;
  }

  // ---------- Disegno ----------
  function render(center) {
    var s = current();
    var sheet = $('sheet');
    $('emptyState').hidden = !!s;
    $('spotState').hidden = !s;
    sheet.classList.toggle('is-empty', !s);
    $('btnCar').hidden = !s;

    if (!s) {
      if (carMarker) { map.removeLayer(carMarker); carMarker = null; }
      $('btnSave').querySelector('.save-label').textContent = 'Salva posizione';
      return;
    }
    shownId = s.id;
    var isLatest = spots[0] && spots[0].id === s.id;
    $('spotEyebrow').textContent = isLatest ? 'Ultimo parcheggio' : 'Parcheggio del ' + when(s.time).replace(/^(oggi|ieri) /, '$1 alle ');
    $('spotPlace').textContent = place(s);
    $('spotAgo').textContent = ago(s.time);
    $('spotWhen').textContent = when(s.time) + (s.acc ? ' · ±' + s.acc + ' m' : '');
    if (document.activeElement !== $('noteInput')) $('noteInput').value = s.note || '';
    $('btnSave').querySelector('.save-label').textContent = 'Salva nuova posizione';
    updateDistance();

    if (!carMarker) {
      carMarker = L.marker([s.lat, s.lng], { icon: carIcon, draggable: false, keyboard: false }).addTo(map);
      carMarker.on('dragstart', function () { carMarker.getElement().querySelector('.car-pin').classList.add('dragging'); });
    } else {
      carMarker.setLatLng([s.lat, s.lng]);
    }
    if (center) focus(s.lat, s.lng);
  }

  function updateDistance() {
    var s = current();
    var show = !!(s && me);
    $('distDot').hidden = !show;
    $('spotDist').textContent = show ? fmtDist(dist(me, s)) : '';
  }

  function renderHistory() {
    var ul = $('historyList');
    $('historyEmpty').hidden = spots.length > 0;
    ul.innerHTML = spots.map(function (s) {
      return '<li class="item' + (s.id === shownId ? ' current' : '') + '" data-id="' + s.id + '">' +
        '<span class="badge"><svg viewBox="0 0 24 24"><path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5a2.5 2.5 0 1 1 0-5 2.5 2.5 0 0 1 0 5z"/></svg></span>' +
        '<span class="txt"><div class="t1">' + esc(place(s)) + '</div><div class="t2">' + when(s.time) + '</div>' +
        (s.note ? '<div class="t3">' + esc(s.note) + '</div>' : '') + '</span>' +
        '<button class="del" aria-label="Elimina"><svg viewBox="0 0 24 24"><path d="M6 19a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg></button></li>';
    }).join('');
  }

  function load() {
    try { spots = JSON.parse(Native.getSpots()) || []; } catch (e) { spots = []; }
  }

  // ---------- Avvisi ----------
  var toastTimer;
  function toast(msg) {
    var t = $('toast');
    t.textContent = msg; t.classList.add('show');
    clearTimeout(toastTimer); toastTimer = setTimeout(function () { t.classList.remove('show'); }, 2600);
  }

  function setBusy(b) {
    var btn = $('btnSave');
    btn.classList.toggle('busy', b);
    btn.querySelector('.save-label').textContent = b ? 'Cerco la posizione…' : (current() ? 'Salva nuova posizione' : 'Salva posizione');
  }

  // ---------- Storico ----------
  function openHistory() { renderHistory(); $('history').classList.add('open'); $('history').setAttribute('aria-hidden', 'false'); }
  function closeHistory() { $('history').classList.remove('open'); $('history').setAttribute('aria-hidden', 'true'); }

  // ---------- Correzione posizione ----------
  function startFix() {
    var s = current(); if (!s || !carMarker) return;
    fixing = true;
    carMarker.dragging.enable();
    $('fixBar').hidden = false;
    focus(s.lat, s.lng, 18);
  }
  function endFix() {
    var s = current();
    fixing = false;
    $('fixBar').hidden = true;
    if (!carMarker) return;
    carMarker.dragging.disable();
    var el = carMarker.getElement(); if (el) el.querySelector('.car-pin').classList.remove('dragging');
    var ll = carMarker.getLatLng();
    if (s && (Math.abs(ll.lat - s.lat) > 1e-7 || Math.abs(ll.lng - s.lng) > 1e-7)) {
      s.lat = ll.lat; s.lng = ll.lng; s.address = '';
      Native.movePosition(s.id, ll.lat, ll.lng);
      render(false);
      toast('Posizione corretta');
    }
  }

  // ---------- Eventi ----------
  $('btnSave').addEventListener('click', function () { if (fixing) endFix(); Native.saveNow(); });
  $('btnGo').addEventListener('click', function () { var s = current(); if (s) Native.navigate(s.lat, s.lng); });
  $('btnShare').addEventListener('click', function () { var s = current(); if (s) Native.share(s.lat, s.lng, s.address || ''); });
  $('btnCar').addEventListener('click', function () { var s = current(); if (s) focus(s.lat, s.lng); });
  $('btnMe').addEventListener('click', function () { toast('Cerco dove sei…'); Native.locateMe(); });
  $('btnHistory').addEventListener('click', openHistory);
  $('btnCloseHistory').addEventListener('click', closeHistory);
  $('btnFix').addEventListener('click', startFix);
  $('btnFixDone').addEventListener('click', endFix);

  var noteTimer;
  $('noteInput').addEventListener('input', function (e) {
    var s = current(); if (!s) return;
    s.note = e.target.value;
    clearTimeout(noteTimer);
    noteTimer = setTimeout(function () { Native.setNote(s.id, s.note); }, 400);
  });
  $('noteInput').addEventListener('blur', function (e) { var s = current(); if (s) Native.setNote(s.id, e.target.value); });
  $('noteInput').addEventListener('keydown', function (e) { if (e.key === 'Enter') e.target.blur(); });

  $('historyList').addEventListener('click', function (e) {
    var li = e.target.closest('.item'); if (!li) return;
    var id = li.getAttribute('data-id');
    if (e.target.closest('.del')) {
      if (!confirm('Eliminare questo parcheggio dallo storico?')) return;
      Native.deleteSpot(id);
      spots = spots.filter(function (s) { return s.id !== id; });
      if (shownId === id) shownId = spots[0] ? spots[0].id : null;
      renderHistory(); render(true);
      return;
    }
    shownId = id; closeHistory(); render(true);
  });

  // ---------- Chiamate dalla parte Android ----------
  window.App = {
    refresh: function () { load(); if (!current()) shownId = null; render(false); updateDistance(); },
    showLatest: function () { closeHistory(); load(); shownId = spots[0] ? spots[0].id : null; render(true); },
    back: function () {
      if ($('history').classList.contains('open')) { closeHistory(); return true; }
      if (fixing) { endFix(); return true; }
      if (spots[0] && shownId !== spots[0].id) { shownId = spots[0].id; render(true); return true; }
      return false;
    },
    onSaving: function () { setBusy(true); },
    onSaved: function (s) {
      setBusy(false); load(); shownId = s.id; render(true);
      toast('Parcheggio salvato');
      if (navigator.vibrate) try { navigator.vibrate(30); } catch (e) {}
    },
    onUpdated: function (s) {
      for (var i = 0; i < spots.length; i++) if (spots[i].id === s.id) { spots[i].address = s.address; }
      if (shownId === s.id) $('spotPlace').textContent = place(current());
      if ($('history').classList.contains('open')) renderHistory();
    },
    onError: function (msg) { setBusy(false); toast(msg); },
    onMyLocation: function (lat, lng) {
      me = { lat: lat, lng: lng };
      if (!meMarker) meMarker = L.marker([lat, lng], { icon: meIcon, interactive: false, zIndexOffset: -10 }).addTo(map);
      else meMarker.setLatLng([lat, lng]);
      updateDistance();
      var s = current();
      if (s) {
        var b = L.latLngBounds([[lat, lng], [s.lat, s.lng]]);
        map.fitBounds(b, { paddingTopLeft: [40, 90], paddingBottomRight: [40, map.getSize().y * 0.42], maxZoom: 18 });
      } else {
        focus(lat, lng, 17);
      }
    },
    onMyLocationError: function (msg) { toast(msg); },
    toast: toast
  };

  // ---------- Avvio ----------
  load();
  render(true);
  if (Native.launchAction && Native.launchAction() === 'latest') window.App.showLatest();
  setInterval(function () { var s = current(); if (s) $('spotAgo').textContent = ago(s.time); }, 30000);
})();
