export default function Login() {
  // SSO-only: no passwords, no reset flow. Edge redirects to providers.
  return (
    <main>
      <h1>Sign in</h1>
      <a href="/api/v1/auth/login/google"><button>Continue with Google</button></a>{' '}
      <a href="/api/v1/auth/login/apple"><button>Continue with Apple</button></a>
    </main>
  );
}
