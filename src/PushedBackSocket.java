import java.io.*;
import java.net.*;

// Wraps a Socket and prepends already-read bytes back into the InputStream.
// Needed because we peek at the first bytes to detect browser vs terminal client.
public class PushedBackSocket extends Socket {

    private final Socket delegate;
    private final PushbackInputStream pushbackStream;

    public PushedBackSocket(Socket socket, byte[] peekedBytes, int length) throws IOException {
        this.delegate = socket;
        PushbackInputStream pb = new PushbackInputStream(socket.getInputStream(), length);
        pb.unread(peekedBytes, 0, length);
        this.pushbackStream = pb;
    }

    @Override
    public InputStream getInputStream() {
        return pushbackStream;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return delegate.getOutputStream();
    }

    @Override
    public boolean isConnected() { return delegate.isConnected(); }

    @Override
    public boolean isClosed() { return delegate.isClosed(); }

    @Override
    public void close() throws IOException { delegate.close(); }

    @Override
    public InetAddress getInetAddress() { return delegate.getInetAddress(); }

    @Override
    public void setSoTimeout(int timeout) throws SocketException {
        delegate.setSoTimeout(timeout);
    }
}
