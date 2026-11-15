/**
 * Pleos Stage - Automatic Server + HTTPS Tunnel Launcher
 * Starts server.js on port 3000 and opens a public HTTPS tunnel (localtunnel)
 * so real smartphones can connect even across isolated Wi-Fi / LTE networks.
 */
const localtunnel = require('localtunnel');
const http = require('http');

// Start the main signaling server in the same process
require('./server.js');

const PORT = process.env.PORT || 3000;

async function startTunnel() {
  try {
    const tunnel = await localtunnel({ port: PORT });
    console.log('==================================================================');
    console.log(` 🌐 HTTPS TUNNEL ACTIVE: ${tunnel.url}`);
    console.log('    (Car QR Code will automatically update with this HTTPS URL)');
    console.log('==================================================================');

    // Notify local server of the tunnel URL so Car QR code updates automatically
    const postData = JSON.stringify({ tunnelUrl: tunnel.url });
    const req = http.request({
      hostname: '127.0.0.1',
      port: PORT,
      path: '/api/tunnel',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(postData)
      }
    });
    req.on('error', () => {});
    req.write(postData);
    req.end();

    tunnel.on('close', () => {
      console.log('[Tunnel] Tunnel closed, reconnecting in 3s...');
      setTimeout(startTunnel, 3000);
    });
  } catch (err) {
    console.warn('[Tunnel] Could not open localtunnel (falling back to Local Wi-Fi IP):', err.message);
  }
}

setTimeout(startTunnel, 1200);
