import { Link, Route, Routes } from 'react-router-dom';
import Dashboard from './pages/Dashboard';
import Login from './pages/Login';
import Resumes from './pages/Resumes';

export default function App() {
  return (
    <div className="mx-auto max-w-4xl p-6 font-sans">
      <header className="mb-6 flex items-center gap-4 border-b pb-4">
        <strong className="text-lg">Resume Builder</strong>
        <Link to="/" className="text-blue-600 hover:underline">Dashboard</Link>
        <Link to="/resumes" className="text-blue-600 hover:underline">Resumes</Link>
        <Link to="/login" className="text-blue-600 hover:underline">Login</Link>
      </header>
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/resumes" element={<Resumes />} />
        <Route path="/login" element={<Login />} />
      </Routes>
    </div>
  );
}
