import { useEffect, useState } from 'react';
import { accountsApi, profilesApi } from '../api/client';

export default function Dashboard() {
  const [email, setEmail] = useState<string>('-');
  const [profiles, setProfiles] = useState<{ id: string; username: string }[]>([]);
  useEffect(() => {
    accountsApi.me().then((a) => setEmail(a.email)).catch(() => setEmail('(signed out)'));
    profilesApi.mine().then(setProfiles).catch(() => setProfiles([]));
  }, []);
  return (
    <main>
      <h1>Dashboard</h1>
      <p>Signed in as: {email}</p>
      <h2>Profiles</h2>
      <ul>{profiles.map((p) => <li key={p.id}>{p.username}</li>)}</ul>
    </main>
  );
}
