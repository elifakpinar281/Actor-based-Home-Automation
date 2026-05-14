export function Topbar() {
    return (
        <header className="bg-purple/20 backdrop-blur border-b border-ink/10 px-8 py-5 flex items-center gap-3">
            <div className="w-11 h-11 rounded-full bg-bg shadow-[var(--shadow-neu-out-sm)] flex items-center justify-center ml-47">
                <img src="/logo_home.png" alt="Logo" />
            </div>

            <h1 className="text-xl font-semibold text-ink">
                Smart Home Automation
            </h1>
        </header>
    );
}