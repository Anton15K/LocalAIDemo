import { ArrowRight, CheckCircle, Brain, Zap, FileText, BookOpen, HelpCircle } from 'lucide-react';
import * as katex from 'katex';
import 'katex/dist/katex.min.css';
import { useEffect, useRef, useState } from 'react';

declare global {
  interface Window {
    Tally?: {
      openPopup: (formId: string, options?: any) => void;
    };
  }
}

function isValidEmail(v: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);
}

function Waitlist() {
  const [email, setEmail] = useState("");
  const [status, setStatus] = useState("");
  const [submitted, setSubmitted] = useState(false);

  const openWaitlist = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = email.trim();

    if (!isValidEmail(trimmed)) {
      setStatus("Please enter a valid email.");
      return;
    }

    setStatus("Joining...");

    try {
      const response = await fetch("http://localhost:8000/waitlist", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ email: trimmed }),
      });

      if (response.ok) {
        setSubmitted(true);
      } else {
        const errorData = await response.json();
        setStatus(errorData.detail || "Something went wrong. Please try again.");
      }
    } catch (error) {
      console.error("Error joining waitlist:", error);
      setStatus("Failed to connect to the server.");
    }
  };

  if (submitted) {
    return (
      <div className="mt-8 p-4 bg-green-50 border border-green-200 rounded-lg text-green-700 font-medium">
        Thanks for joining the waitlist! We'll be in touch soon.
      </div>
    );
  }

  return (
    <form onSubmit={openWaitlist} className="mt-8 flex flex-col items-center justify-center gap-3 w-full max-w-2xl mx-auto" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      <div className="w-full" style={{ width: '100%' }}>
        <input
          type="email"
          placeholder="you@company.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
          className="w-full px-4 py-3 rounded-lg border border-gray-300 focus:ring-2 focus:ring-orange-500 focus:border-orange-500 outline-none transition-all"
          style={{ width: '100%', display: 'block' }}
        />
      </div>
      <button 
        type="submit"
        className="w-full px-6 py-3 bg-orange-500 text-white font-medium rounded-lg hover:bg-orange-600 transition-colors whitespace-nowrap"
        style={{ display: 'block', width: '100%' }}
      >
        Join the waitlist
      </button>
      <div className="text-sm text-gray-500 font-medium">
        Early access for students & educators • No spam
      </div>
      {status && <div className="text-sm text-red-600 font-medium mt-2">{status}</div>}
    </form>
  );
}

function Math({ formula, displayMode = false }: { formula: string; displayMode?: boolean }) {
  const containerRef = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    if (containerRef.current) {
      katex.render(formula, containerRef.current, {
        throwOnError: false,
        displayMode: displayMode
      });
    }
  }, [formula, displayMode]);

  return <span ref={containerRef} />;
}

export default function App() {
  return (
    <div className="min-h-screen bg-white">
      {/* Header */}
      <header className="border-b border-gray-200">
        <div className="max-w-5xl mx-auto px-6 py-4 flex items-center justify-between">
          <div className="text-xl font-semibold text-gray-900">Erudit AI</div>
          <a href="/dashboard" className="px-4 py-2 text-sm font-medium text-gray-700 hover:text-gray-900">
            Sign in
          </a>
        </div>
      </header>

      {/* Hero Section */}
      <section className="max-w-5xl mx-auto px-6 pt-16 pb-20">
        <div className="text-center mb-12">
          <h1 className="text-5xl font-extrabold text-gray-900 mb-6 leading-tight">
              <span className="text-orange-600">Practice</span> what you just learned
          </h1>
          <p className="text-2xl text-gray-600 max-w-2xl mx-auto leading-relaxed mb-6">
            Erudit AI turns lectures into <span className="font-semibold text-gray-900">problems you can solve</span> and learn from.
          </p>
          <div className="bg-orange-50 border-l-4 border-orange-500 py-3 px-6 mb-8 max-w-2xl mx-auto rounded-r-lg">
            <p className="text-gray-800 font-medium">
              Built for students who want to <span className="text-orange-700 font-bold">actually understand</span> their lectures — not just reread notes.
            </p>
          </div>
          <p className="text-lg text-gray-600 max-w-2xl mx-auto leading-relaxed mb-8">
            Practice with questions <span className="italic">generated directly from your lectures</span>—supported by clear, structured notes when you need them.
          </p>
          <Waitlist />
          
          {/* Feature badges */}
          <div className="mt-8 flex flex-wrap items-center justify-center gap-3 text-sm text-gray-600">
            <div className="flex items-center gap-1.5">
              <span className="text-orange-600">✓</span>
              Open-answer problems
            </div>
            <div className="flex items-center gap-1.5">
              <span className="text-orange-600">✓</span>
              Answer-only questions
            </div>
            <div className="flex items-center gap-1.5">
              <span className="text-orange-600">✓</span>
              Multiple-choice quizzes
            </div>
          </div>
        </div>

        {/* Example Problem Card */}
        <div className="text-center mb-6">
          <span className="text-sm font-medium text-orange-600 bg-orange-50 px-3 py-1">
            Preview of how Erudit AI works
          </span>
        </div>
        <div className="max-w-3xl mx-auto border border-gray-200 rounded-lg overflow-hidden shadow-sm bg-white">
          <div className="bg-white p-6 border-b border-gray-200">
            <div className="text-xs font-semibold text-gray-500 tracking-wider mb-4">PROBLEM FROM YOUR LECTURE</div>
            <div className="text-gray-900">
              <p className="text-base mb-3">Calculate the limit:</p>
              <div className="bg-gray-50 p-6 rounded-lg border border-gray-200 flex justify-center">
                <div className="text-sm">
                  <Math formula="\lim_{x \to 0} \frac{e^{2x} - 1 - 2x}{x^2}" displayMode={true} />
                </div>
              </div>
            </div>
          </div>
          
          <div className="bg-blue-50 p-6 border-b border-blue-100">
            <div className="text-xs font-semibold text-blue-800 tracking-wider mb-4">YOUR ANSWER</div>
            <div className="text-gray-800 space-y-3">
              <div className="bg-white p-4 rounded border border-blue-200 shadow-sm">
                <p className="leading-relaxed mb-4 text-base">This is <Math formula="0/0" /> form, so I'll use L'Hôpital's rule twice:</p>
                <div className="space-y-4">
                  <div className="flex items-baseline gap-2">
                    <span className="text-blue-900 font-semibold shrink-0">First derivative:</span>
                    <div className="bg-blue-50/50 p-2 rounded">
                      <Math formula="\lim_{x \to 0} \frac{2e^{2x} - 2}{2x}" />
                    </div>
                  </div>
                  <div className="flex items-baseline gap-2">
                    <span className="text-blue-900 font-semibold shrink-0">Apply again:</span>
                    <div className="bg-blue-50/50 p-2 rounded">
                      <Math formula="\lim_{x \to 0} \frac{4e^{2x}}{2} = \frac{4}{2} = 2" />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          
          <div className="bg-white p-6">
            <div className="flex items-center gap-2 mb-4">
              <CheckCircle className="w-4 h-4 text-green-600" />
              <div className="text-xs font-semibold text-green-700 tracking-wider">AI FEEDBACK</div>
            </div>
            <div className="space-y-4">
              <div>
                <p className="text-lg font-semibold text-green-700 mb-2">Correct! Your answer of 2 is right.</p>
                <p className="text-gray-700 leading-relaxed">
                  You properly identified the indeterminate form and applied L'Hôpital's rule twice. Your differentiation was accurate at each step.
                </p>
              </div>
              
              <div className="bg-amber-50 border border-amber-200 rounded-lg p-4">
                <p className="text-sm font-semibold text-amber-900 mb-2">💡 Alternative approach</p>
                <div className="text-sm text-gray-700 leading-relaxed">
                  You could also use the Taylor series expansion <Math formula="e^{2x} = 1 + 2x + \frac{(2x)^2}{2!} + \dots" /> which gives <Math formula="\frac{1 + 2x + 2x^2 + \dots - 1 - 2x}{x^2} = 2" />.
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Why Practice Works */}
      <section className="bg-gray-50 py-16">
        <div className="max-w-5xl mx-auto px-6">
          <div className="max-w-2xl mx-auto text-center">
            <h2 className="text-3xl font-semibold text-gray-900 mb-4">
              Practice beats passive review
            </h2>
            <p className="text-lg text-gray-600 leading-relaxed">
              Rereading notes feels productive but doesn't build understanding. 
              Active practice reveals what you actually know and forces you to work through problems from scratch. 
              Erudit AI makes it easy to practice continuously, not just before exams.
            </p>
          </div>
        </div>
      </section>

      {/* How It Works */}
      <section className="py-20 max-w-5xl mx-auto px-6">
        <h2 className="text-3xl font-semibold text-gray-900 text-center mb-12">
          How it works
        </h2>
        <div className="grid md:grid-cols-3 gap-8">
          <div className="text-center">
            <div className="w-12 h-12 bg-orange-100 rounded-lg flex items-center justify-center mx-auto mb-4">
              <FileText className="w-6 h-6 text-orange-600" />
            </div>
            <h3 className="font-semibold text-gray-900 mb-2">1. Upload your materials</h3>
            <p className="text-gray-600">
              Drop in lecture videos, slides, or textbook chapters. Erudit AI extracts the key concepts.
            </p>
          </div>
          
          <div className="text-center">
            <div className="w-12 h-12 bg-orange-100 rounded-lg flex items-center justify-center mx-auto mb-4">
              <Zap className="w-6 h-6 text-orange-600" />
            </div>
            <h3 className="font-semibold text-gray-900 mb-2">2. Get practice problems</h3>
            <p className="text-gray-600">
              AI generates problems you can solve right away. Practice while learning, during homework, or before exams.
            </p>
          </div>
          
          <div className="text-center">
            <div className="w-12 h-12 bg-orange-100 rounded-lg flex items-center justify-center mx-auto mb-4">
              <Brain className="w-6 h-6 text-orange-600" />
            </div>
            <h3 className="font-semibold text-gray-900 mb-2">3. Get instant feedback</h3>
            <p className="text-gray-600">
              AI evaluates your reasoning and methodology, not just keywords. Know exactly what you got wrong and why.
            </p>
          </div>
        </div>
      </section>

      {/* Support Throughout Learning */}
      <section className="bg-gray-50 py-16">
        <div className="max-w-4xl mx-auto px-6">
          <h2 className="text-3xl font-semibold text-gray-900 text-center mb-10">
            Practice throughout your studies
          </h2>
          <div className="grid md:grid-cols-3 gap-6">
            <div className="bg-white p-6 rounded-lg border border-gray-200">
              <h3 className="font-semibold text-gray-900 mb-2">Practice while learning</h3>
              <p className="text-gray-600 text-sm">
                Generate problems right after watching a lecture to reinforce new concepts immediately.
              </p>
            </div>
            <div className="bg-white p-6 rounded-lg border border-gray-200">
              <h3 className="font-semibold text-gray-900 mb-2">Use as homework practice</h3>
              <p className="text-gray-600 text-sm">
                Work through similar problems to understand the material before tackling assignments.
              </p>
            </div>
            <div className="bg-white p-6 rounded-lg border border-gray-200">
              <h3 className="font-semibold text-gray-900 mb-2">Practice before exams</h3>
              <p className="text-gray-600 text-sm">
                Test yourself with problems from all your lectures to identify weak spots early.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Problem Types */}
      <section className="py-20 max-w-5xl mx-auto px-6">
        <h2 className="text-3xl font-semibold text-gray-900 text-center mb-4">
          Supported problem types
        </h2>
        <p className="text-center text-gray-600 mb-12 max-w-2xl mx-auto">
          Erudit AI adapts to different subjects and question formats
        </p>
        
        <div className="grid md:grid-cols-3 gap-6 max-w-4xl mx-auto">
          <div className="border border-gray-200 rounded-lg p-6 bg-white">
            <div className="w-10 h-10 bg-purple-100 rounded-lg flex items-center justify-center mb-4">
              <div className="text-2xl">📝</div>
            </div>
            <h3 className="font-semibold text-gray-900 mb-2">Open-answer problems</h3>
            <p className="text-sm text-gray-600 mb-4">
              Work through full solutions with written explanations. AI evaluates your reasoning, not just the final answer.
            </p>
            <div className="bg-gray-50 p-3 rounded text-xs text-gray-700 font-mono">
              "Explain the difference between supervised and unsupervised learning"
            </div>
          </div>

          <div className="border border-gray-200 rounded-lg p-6 bg-white">
            <div className="w-10 h-10 bg-blue-100 rounded-lg flex items-center justify-center mb-4">
              <div className="text-2xl">✏️</div>
            </div>
            <h3 className="font-semibold text-gray-900 mb-2">Answer-only questions</h3>
            <p className="text-sm text-gray-600 mb-4">
              Solve numerical or short-answer problems. Perfect for math, physics, and chemistry practice.
            </p>
            <div className="bg-gray-50 p-3 rounded text-xs text-gray-700 font-mono">
              <Math formula="\text{Calculate the derivative of } f(x) = 3x^2 + 2x - 5" />
            </div>
          </div>

          <div className="border border-gray-200 rounded-lg p-6 bg-white">
            <div className="w-10 h-10 bg-green-100 rounded-lg flex items-center justify-center mb-4">
              <div className="text-2xl">☑️</div>
            </div>
            <h3 className="font-semibold text-gray-900 mb-2">Multiple-choice quizzes</h3>
            <p className="text-sm text-gray-600 mb-4">
              Quick concept checks to test your understanding. Get explanations for why each answer is right or wrong.
            </p>
            <div className="bg-gray-50 p-3 rounded text-xs text-gray-700">
              <div className="mb-1">□ Mitochondria</div>
              <div className="mb-1">□ Nucleus</div>
              <div>□ Ribosome</div>
            </div>
          </div>
        </div>
      </section>

      {/* AI Feedback Section */}
      <section className="bg-gray-50 py-16">
        <div className="max-w-4xl mx-auto px-6">
          <h2 className="text-3xl font-semibold text-gray-900 text-center mb-10">
            AI that checks your work properly
          </h2>
          
          <div className="bg-white border border-gray-200 rounded-lg p-8">
            <div className="grid md:grid-cols-2 gap-8">
              <div>
                <div className="text-sm font-medium text-red-600 mb-3">❌ KEYWORD MATCHING</div>
                <div className="text-sm text-gray-600 space-y-2">
                  <p>"Your answer contains the word 'photosynthesis' ✓"</p>
                  <p className="italic text-gray-500">Marks correct even if reasoning is wrong</p>
                </div>
              </div>
              <div>
                <div className="text-sm font-medium text-green-600 mb-3">✓ ERUDIT AI</div>
                <div className="text-sm text-gray-600 space-y-2">
                  <p>"You correctly identified photosynthesis but your explanation of the light-dependent reactions is incomplete. You're missing the role of NADPH..."</p>
                  <p className="italic text-gray-500">Evaluates understanding and methodology</p>
                </div>
              </div>
            </div>

            <div className="mt-8 pt-8 border-t border-gray-200">
              <h3 className="font-semibold text-gray-900 mb-4">Feedback includes:</h3>
              <div className="grid md:grid-cols-2 gap-4">
                <div className="flex gap-3">
                  <CheckCircle className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
                  <div>
                    <div className="font-medium text-gray-900 text-sm">What you got right</div>
                    <div className="text-sm text-gray-600">Confirms correct reasoning steps</div>
                  </div>
                </div>
                <div className="flex gap-3">
                  <CheckCircle className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
                  <div>
                    <div className="font-medium text-gray-900 text-sm">Where you went wrong</div>
                    <div className="text-sm text-gray-600">Pinpoints specific mistakes</div>
                  </div>
                </div>
                <div className="flex gap-3">
                  <CheckCircle className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
                  <div>
                    <div className="font-medium text-gray-900 text-sm">What you're missing</div>
                    <div className="text-sm text-gray-600">Identifies gaps in your solution</div>
                  </div>
                </div>
                <div className="flex gap-3">
                  <CheckCircle className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
                  <div>
                    <div className="font-medium text-gray-900 text-sm">Alternative approaches</div>
                    <div className="text-sm text-gray-600">Shows other ways to solve it</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Get Help When You Need It */}
      <section className="py-16 max-w-5xl mx-auto px-6">
        <h2 className="text-3xl font-semibold text-gray-900 text-center mb-10">
          Get help when you need it
        </h2>
        
        <div className="grid md:grid-cols-2 gap-6 max-w-4xl mx-auto">
          <div className="border border-gray-200 rounded-lg p-6 bg-white">
            <div className="flex items-start gap-4">
              <div className="w-12 h-12 bg-blue-100 rounded-lg flex items-center justify-center flex-shrink-0">
                <HelpCircle className="w-6 h-6 text-blue-600" />
              </div>
              <div>
                <h3 className="font-semibold text-gray-900 mb-2">Stuck on a problem? Ask for the answer</h3>
                <p className="text-sm text-gray-600 mb-3">
                  Get step-by-step explanations for any practice problem. Understand the solution method, not just the final answer.
                </p>
                <div className="bg-gray-50 p-3 rounded text-xs text-gray-700 border border-gray-200">
                  <div className="font-medium mb-1">Example explanation:</div>
                  <div className="text-gray-600">
                    "To solve this integral, we use substitution. Let <Math formula="u = 3x" />, then <Math formula="du = 3dx" />..."
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="border border-gray-200 rounded-lg p-6 bg-white">
            <div className="flex items-start gap-4">
              <div className="w-12 h-12 bg-purple-100 rounded-lg flex items-center justify-center flex-shrink-0">
                <BookOpen className="w-6 h-6 text-purple-600" />
              </div>
              <div>
                <h3 className="font-semibold text-gray-900 mb-2">Need to review? Check your notes</h3>
                <p className="text-sm text-gray-600 mb-3">
                  Erudit AI generates structured notes from your video lectures for quick reference when you need to look something up.
                </p>
                <div className="bg-gray-50 p-3 rounded text-xs text-gray-700 border border-gray-200">
                  <div className="font-medium mb-1">Auto-generated from lectures:</div>
                  <div className="text-gray-600">
                    • Key concepts and definitions<br/>
                    • Important formulas<br/>
                    • Supporting context
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="text-center mt-6">
          <p className="text-sm text-gray-500 italic">
            Notes and explanations support your practice—they're there when you need them
          </p>
        </div>
      </section>

      {/* Conceptual Diagram */}
      <section className="py-16 max-w-4xl mx-auto px-6">
        <div className="flex items-center justify-center gap-6 md:gap-12">
          <div className="text-center">
            <div className="w-20 h-20 bg-blue-100 rounded-lg flex items-center justify-center mx-auto mb-3">
              <FileText className="w-10 h-10 text-blue-600" />
            </div>
            <div className="text-sm font-medium text-gray-900">Your lecture</div>
          </div>
          
          <div className="text-gray-400">
            <ArrowRight className="w-8 h-8" />
          </div>
          
          <div className="text-center">
            <div className="w-20 h-20 bg-orange-100 rounded-lg flex items-center justify-center mx-auto mb-3">
              <Zap className="w-10 h-10 text-orange-600" />
            </div>
            <div className="text-sm font-medium text-gray-900">Practice problems</div>
          </div>
          
          <div className="text-gray-400">
            <ArrowRight className="w-8 h-8" />
          </div>
          
          <div className="text-center">
            <div className="w-20 h-20 bg-green-100 rounded-lg flex items-center justify-center mx-auto mb-3">
              <Brain className="w-10 h-10 text-green-600" />
            </div>
            <div className="text-sm font-medium text-gray-900">AI feedback</div>
          </div>
        </div>
      </section>


      {/* Final CTA */}
      <section className="bg-gray-900 py-16">
        <div className="max-w-4xl mx-auto px-6 text-center">
          <h2 className="text-3xl font-semibold text-white mb-4">
            Stop rereading. Start practicing.
          </h2>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-gray-200 py-8">
        <div className="max-w-5xl mx-auto px-6 text-center text-sm text-gray-500">
          © 2026 Erudit AI. Built for students who want to actually learn.
        </div>
      </footer>
    </div>
  );
}